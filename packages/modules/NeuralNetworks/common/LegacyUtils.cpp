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

#define LOG_TAG "Utils"

#include "LegacyUtils.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <errno.h>
#include <nnapi/TypeUtils.h>
#include <poll.h>

#include <algorithm>
#include <functional>
#include <limits>
#include <numeric>
#include <string>
#include <tuple>
#include <unordered_map>
#include <utility>
#include <vector>

#include "ControlFlow.h"
#include "NeuralNetworks.h"
#include "NeuralNetworksOEM.h"
#include "OperationResolver.h"

namespace android {
namespace nn {

std::ostream& operator<<(std::ostream& os, const HalVersion& halVersion) {
    switch (halVersion) {
        case HalVersion::UNKNOWN:
            return os << "UNKNOWN HAL version";
        case HalVersion::V1_0:
            return os << "HAL version 1.0";
        case HalVersion::V1_1:
            return os << "HAL version 1.1";
        case HalVersion::V1_2:
            return os << "HAL version 1.2";
        case HalVersion::V1_3:
            return os << "HAL version 1.3";
        case HalVersion::AIDL_V1:
            return os << "HAL version AIDL_V1";
        case HalVersion::AIDL_V2:
            return os << "HAL version AIDL_V2";
        case HalVersion::AIDL_UNSTABLE:
            return os << "HAL uses unstable AIDL";
    }
    return os << "HalVersion{" << static_cast<int32_t>(halVersion) << "}";
}

Duration makeTimeoutDuration(uint64_t nanoseconds) {
    constexpr auto kMaxCount = Duration::max().count();
    using CommonType = std::common_type_t<Duration::rep, uint64_t>;
    const auto count = std::min<CommonType>(kMaxCount, nanoseconds);
    return Duration{static_cast<Duration::rep>(count)};
}

OptionalDuration makeTimeoutDuration(int64_t nanoseconds) {
    CHECK_GE(nanoseconds, -1);
    if (nanoseconds == -1) {
        return OptionalDuration{};
    }
    return makeTimeoutDuration(static_cast<uint64_t>(nanoseconds));
}

TimePoint makeDeadline(Duration duration) {
    constexpr auto kMaxTime = TimePoint::max();
    const auto currentTime = Clock::now();

    // If there would be an overflow, use the max value.
    if (duration > kMaxTime - currentTime) {
        return kMaxTime;
    }
    return currentTime + duration;
}

bool hasDeadlinePassed(const OptionalTimePoint& deadline) {
    if (!deadline.has_value()) {
        return false;
    }
    return Clock::now() >= *deadline;
}

static bool isExtensionOperandType(int32_t type) {
    return (static_cast<uint32_t>(type) >> kExtensionTypeBits) != 0;
}

static bool isExtensionOperationType(ANeuralNetworksOperationType type) {
    return (static_cast<uint32_t>(type) >> kExtensionTypeBits) != 0;
}

bool isExtensionOperandType(OperandType type) {
    return isExtensionOperandType(static_cast<int32_t>(type));
}

bool isExtensionOperationType(OperationType type) {
    return isExtensionOperationType(static_cast<int32_t>(type));
}

namespace {

template <typename EntryType, uint32_t entryCount, uint32_t entryCountOEM>
EntryType tableLookup(const EntryType (&table)[entryCount],
                      const EntryType (&tableOEM)[entryCountOEM], uint32_t code) {
    if (code < entryCount) {
        return table[code];
    } else if (code >= kOEMCodeBase && (code - kOEMCodeBase) < entryCountOEM) {
        return tableOEM[code - kOEMCodeBase];
    } else {
        LOG(FATAL) << "tableLookup: bad code";
        return EntryType();
    }
}

static Version convert(HalVersion halVersion) {
    switch (halVersion) {
        case HalVersion::UNKNOWN:
            break;
        case HalVersion::V1_0:
            return kVersionFeatureLevel1;
        case HalVersion::V1_1:
            return kVersionFeatureLevel2;
        case HalVersion::V1_2:
            return kVersionFeatureLevel3;
        case HalVersion::V1_3:
            return kVersionFeatureLevel4;
        case HalVersion::AIDL_V1:
            return kVersionFeatureLevel5;
        case HalVersion::AIDL_V2:
            return kVersionFeatureLevel6;
        case HalVersion::AIDL_UNSTABLE:
            return kVersionFeatureLevel7;
    }
    LOG(FATAL) << "Cannot convert " << halVersion;
    return {};
}

class OperationValidationContext : public IOperationValidationContext {
    DISALLOW_IMPLICIT_CONSTRUCTORS(OperationValidationContext);

   public:
    OperationValidationContext(const char* operationName, uint32_t inputCount,
                               const uint32_t* inputIndexes, uint32_t outputCount,
                               const uint32_t* outputIndexes, const Operand* operands)
        : operationName(operationName),
          inputCount(inputCount),
          inputIndexes(inputIndexes),
          outputCount(outputCount),
          outputIndexes(outputIndexes),
          operands(operands) {}

    const char* getOperationName() const override;

    uint32_t getNumInputs() const override;
    OperandType getInputType(uint32_t index) const override;
    Shape getInputShape(uint32_t index) const override;
    const Operand::ExtraParams& getInputExtraParams(uint32_t index) const override;

    uint32_t getNumOutputs() const override;
    OperandType getOutputType(uint32_t index) const override;
    Shape getOutputShape(uint32_t index) const override;

   private:
    const Operand* getInputOperand(uint32_t index) const;
    const Operand* getOutputOperand(uint32_t index) const;

    const char* operationName;
    uint32_t inputCount;
    const uint32_t* inputIndexes;
    uint32_t outputCount;
    const uint32_t* outputIndexes;
    const Operand* operands;
};

const char* OperationValidationContext::getOperationName() const {
    return operationName;
}

const Operand* OperationValidationContext::getInputOperand(uint32_t index) const {
    CHECK(index < static_cast<uint32_t>(inputCount));
    return &operands[inputIndexes[index]];
}

const Operand* OperationValidationContext::getOutputOperand(uint32_t index) const {
    CHECK(index < static_cast<uint32_t>(outputCount));
    return &operands[outputIndexes[index]];
}

uint32_t OperationValidationContext::getNumInputs() const {
    return inputCount;
}

uint32_t OperationValidationContext::getNumOutputs() const {
    return outputCount;
}

OperandType OperationValidationContext::getInputType(uint32_t index) const {
    return getInputOperand(index)->type;
}

Shape OperationValidationContext::getInputShape(uint32_t index) const {
    const Operand* operand = getInputOperand(index);
    return {operand->type, operand->dimensions, operand->scale, operand->zeroPoint,
            operand->extraParams};
}

const Operand::ExtraParams& OperationValidationContext::getInputExtraParams(uint32_t index) const {
    return getInputOperand(index)->extraParams;
}

OperandType OperationValidationContext::getOutputType(uint32_t index) const {
    return getOutputOperand(index)->type;
}

Shape OperationValidationContext::getOutputShape(uint32_t index) const {
    const Operand* operand = getOutputOperand(index);
    return {operand->type, operand->dimensions, operand->scale, operand->zeroPoint,
            operand->extraParams};
}

};  // anonymous namespace

#define COUNT(X) (sizeof(X) / sizeof(X[0]))

const uint32_t kSizeOfDataType[]{
        4,  // ANEURALNETWORKS_FLOAT32
        4,  // ANEURALNETWORKS_INT32
        4,  // ANEURALNETWORKS_UINT32
        4,  // ANEURALNETWORKS_TENSOR_FLOAT32
        4,  // ANEURALNETWORKS_TENSOR_INT32
        1,  // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM
        1,  // ANEURALNETWORKS_BOOL
        2,  // ANEURALNETWORKS_TENSOR_QUANT16_SYMM
        2,  // ANEURALNETWORKS_TENSOR_FLOAT16
        1,  // ANEURALNETWORKS_TENSOR_BOOL8
        2,  // ANEURALNETWORKS_FLOAT16
        1,  // ANEURALNETWORKS_TENSOR_QUANT8_SYMM_PER_CHANNEL
        2,  // ANEURALNETWORKS_TENSOR_QUANT16_ASYMM
        1,  // ANEURALNETWORKS_TENSOR_QUANT8_SYMM
        1,  // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM_SIGNED
        0,  // ANEURALNETWORKS_MODEL
};

static_assert(COUNT(kSizeOfDataType) == kNumberOfDataTypes, "kSizeOfDataType is incorrect");

const bool kScalarDataType[]{
        true,   // ANEURALNETWORKS_FLOAT32
        true,   // ANEURALNETWORKS_INT32
        true,   // ANEURALNETWORKS_UINT32
        false,  // ANEURALNETWORKS_TENSOR_FLOAT32
        false,  // ANEURALNETWORKS_TENSOR_INT32
        false,  // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM
        true,   // ANEURALNETWORKS_BOOL
        false,  // ANEURALNETWORKS_TENSOR_QUANT16_SYMM
        false,  // ANEURALNETWORKS_TENSOR_FLOAT16
        false,  // ANEURALNETWORKS_TENSOR_BOOL8
        true,   // ANEURALNETWORKS_FLOAT16
        false,  // ANEURALNETWORKS_TENSOR_QUANT8_SYMM_PER_CHANNEL
        false,  // ANEURALNETWORKS_TENSOR_QUANT16_ASYMM
        false,  // ANEURALNETWORKS_TENSOR_QUANT8_SYMM
        false,  // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM_SIGNED
        true,   // ANEURALNETWORKS_MODEL
};

static_assert(COUNT(kScalarDataType) == kNumberOfDataTypes, "kScalarDataType is incorrect");

const uint32_t kSizeOfDataTypeOEM[]{
        0,  // ANEURALNETWORKS_OEM
        1,  // ANEURALNETWORKS_TENSOR_OEM_BYTE
};

static_assert(COUNT(kSizeOfDataTypeOEM) == kNumberOfDataTypesOEM,
              "kSizeOfDataTypeOEM is incorrect");

const bool kScalarDataTypeOEM[]{
        true,   // ANEURALNETWORKS_OEM
        false,  // ANEURALNETWORKS_TENSOR_OEM_BYTE
};

static_assert(COUNT(kScalarDataTypeOEM) == kNumberOfDataTypesOEM,
              "kScalarDataTypeOEM is incorrect");

bool nonExtensionOperandTypeIsScalar(int type) {
    CHECK(!isExtensionOperandType(type)) << "Extension operand types are not supported";
    return tableLookup(kScalarDataType, kScalarDataTypeOEM, type);
}

uint32_t nonExtensionOperandSizeOfData(OperandType type, const std::vector<uint32_t>& dimensions) {
    const size_t size = getNonExtensionSize(type, dimensions).value();
    CHECK_LE(size, std::numeric_limits<uint32_t>::max());
    return size;
}

// Returns a pair of {false, size} on success, {true, 0} if size overflows uint32_t.
static std::pair<bool, uint32_t> sizeOfTensorDataHelper(uint32_t sizeOfElement,
                                                        const std::vector<uint32_t>& dimensions) {
    if (dimensions.empty()) {
        return {false, 0};
    }
    uint64_t size = static_cast<uint64_t>(sizeOfElement);
    constexpr uint64_t kMaxSize = static_cast<uint64_t>(std::numeric_limits<uint32_t>::max());
    for (uint32_t d : dimensions) {
        size *= d;
        if (size > kMaxSize) return {true, 0};
    }
    return {false, static_cast<uint32_t>(size)};
}

uint32_t sizeOfTensorData(uint32_t sizeOfElement, const std::vector<uint32_t>& dimensions) {
    const auto [overflow, size] = sizeOfTensorDataHelper(sizeOfElement, dimensions);
    CHECK(!overflow);
    return size;
}

bool nonExtensionOperandSizeOfDataOverflowsUInt32(OperandType type,
                                                  const std::vector<uint32_t>& dimensions) {
    CHECK(!isExtension(type)) << "Size of extension operand data is unknown";
    int n = static_cast<int>(type);
    uint32_t sizeOfElement = tableLookup(kSizeOfDataType, kSizeOfDataTypeOEM, n);
    return tableLookup(kScalarDataType, kScalarDataTypeOEM, n)
                   ? false
                   : sizeOfTensorDataOverflowsUInt32(sizeOfElement, dimensions);
}

bool sizeOfTensorDataOverflowsUInt32(uint32_t sizeOfElement,
                                     const std::vector<uint32_t>& dimensions) {
    return sizeOfTensorDataHelper(sizeOfElement, dimensions).first;
}

bool tensorHasUnspecifiedDimensions(int type, const uint32_t* dim, uint32_t dimCount) {
    if (!isExtensionOperandType(type)) {
        CHECK(!nonExtensionOperandTypeIsScalar(type))
                << "A scalar type can never have unspecified dimensions";
    }
    return dimCount == 0 || std::find(dim, dim + dimCount, 0) != (dim + dimCount);
}

bool tensorHasUnspecifiedDimensions(const ANeuralNetworksOperandType* type) {
    return tensorHasUnspecifiedDimensions(type->type, type->dimensions, type->dimensionCount);
}

uint32_t alignBytesNeeded(uint32_t index, size_t length) {
    uint32_t alignment = getAlignmentForLength(length);
    uint32_t pattern = alignment - 1;
    uint32_t extra = (~(index - 1)) & pattern;
    return extra;
}

void logModelToInfo(const Model& model) {
    LOG(INFO) << model;
}

static bool validateScalarDimensions(const ANeuralNetworksOperandType& type, const char* tag) {
    NN_RET_CHECK_EQ(type.dimensionCount, 0u) << tag << " invalid dimensions for scalar type";
    NN_RET_CHECK(type.dimensions == nullptr) << tag << " invalid dimensions for scalar type";
    return true;
}

static bool validateQuant8AsymmParams(const ANeuralNetworksOperandType& type, const char* tag) {
    NN_RET_CHECK(0 <= type.zeroPoint && type.zeroPoint <= 255)
            << tag << " invalid zeroPoint: " << type.zeroPoint;
    NN_RET_CHECK_GT(type.scale, 0.f) << tag << " invalid scale";
    return true;
}

static bool validateQuant8AsymmSignedParams(const ANeuralNetworksOperandType& type,
                                            const char* tag) {
    NN_RET_CHECK(-128 <= type.zeroPoint && type.zeroPoint <= 127)
            << tag << " invalid zeroPoint: " << type.zeroPoint;
    NN_RET_CHECK_GT(type.scale, 0.f) << tag << " invalid scale";
    return true;
}

static bool validateQuant8SymmParams(const ANeuralNetworksOperandType& type, const char* tag) {
    NN_RET_CHECK_EQ(type.zeroPoint, 0) << tag << " invalid zeroPoint: " << type.zeroPoint;
    NN_RET_CHECK_GT(type.scale, 0.f) << tag << " invalid scale";
    return true;
}

static bool validateQuant16AsymmParams(const ANeuralNetworksOperandType& type, const char* tag) {
    NN_RET_CHECK(0 <= type.zeroPoint && type.zeroPoint <= 65535)
            << tag << " invalid zeroPoint: " << type.zeroPoint;
    NN_RET_CHECK_GT(type.scale, 0.f) << tag << " invalid scale";
    return true;
}

static bool validateQuantSymmParams(const ANeuralNetworksOperandType& type, const char* tag) {
    NN_RET_CHECK_EQ(type.zeroPoint, 0) << tag << " zeroPoint is not zero";
    NN_RET_CHECK_GT(type.scale, 0.f) << tag << " invalid scale";
    return true;
}

static bool validateNoQuantParams(const ANeuralNetworksOperandType& type, const char* tag) {
    NN_RET_CHECK_EQ(type.zeroPoint, 0) << tag << " zeroPoint is not zero";
    NN_RET_CHECK_EQ(type.scale, 0.f) << tag << " scale is not zero";
    return true;
}

static bool validateTensorDimensions(
        const ANeuralNetworksOperandType& type,
        const Extension::OperandTypeInformation* const extensionOperandTypeInfo, const char* tag,
        bool allowPartial) {
    if (!allowPartial) {
        NN_RET_CHECK_GT(type.dimensionCount, 0u) << tag << " invalid operand dimensions";
    }
    uint64_t size =
            isExtensionOperandType(type.type)
                    ? extensionOperandTypeInfo->byteSize
                    : tableLookup(kSizeOfDataType, kSizeOfDataTypeOEM, static_cast<int>(type.type));
    constexpr uint64_t kMaxSize = std::numeric_limits<uint32_t>::max();
    for (uint32_t i = 0; i < type.dimensionCount; i++) {
        if (!allowPartial) {
            NN_RET_CHECK_NE(type.dimensions[i], 0u) << tag << " invalid operand dimensions";
        }
        if (type.dimensions[i] != 0) {
            size *= type.dimensions[i];
            NN_RET_CHECK_LE(size, kMaxSize) << tag << " operand byte size exceeds " << kMaxSize;
        }
    }
    return true;
}

static bool validateOperandTypeHelper(
        const ANeuralNetworksOperandType& type,
        const Extension::OperandTypeInformation* const extensionOperandTypeInfo, const char* tag,
        bool allowPartial) {
    NN_RET_CHECK_EQ(type.dimensionCount == 0, type.dimensions == nullptr);
    if (isExtensionOperandType(type.type)) {
        NN_RET_CHECK(extensionOperandTypeInfo != nullptr);
        if (extensionOperandTypeInfo->isTensor) {
            NN_RET_CHECK(
                    validateTensorDimensions(type, extensionOperandTypeInfo, tag, allowPartial));
        } else {
            NN_RET_CHECK(validateScalarDimensions(type, tag));
        }
        return validateNoQuantParams(type, tag);
    }

    NN_RET_CHECK(extensionOperandTypeInfo == nullptr);
    NN_RET_CHECK(validCode(kNumberOfDataTypes, kNumberOfDataTypesOEM, type.type))
            << tag << " invalid OperandType: " << type.type;

    bool isScalar = tableLookup(kScalarDataType, kScalarDataTypeOEM, type.type);
    if (isScalar) {
        NN_RET_CHECK(validateScalarDimensions(type, tag));
        if (type.type != ANEURALNETWORKS_OEM_SCALAR) {  // Historically, we have allowed OEM types
                                                        // to use quantization parameters.
            NN_RET_CHECK(validateNoQuantParams(type, tag));
        }
    } else {
        NN_RET_CHECK(validateTensorDimensions(type, extensionOperandTypeInfo, tag, allowPartial));
        if (type.type == ANEURALNETWORKS_TENSOR_QUANT8_ASYMM) {
            NN_RET_CHECK(validateQuant8AsymmParams(type, tag));
        } else if (type.type == ANEURALNETWORKS_TENSOR_QUANT8_ASYMM_SIGNED) {
            NN_RET_CHECK(validateQuant8AsymmSignedParams(type, tag));
        } else if (type.type == ANEURALNETWORKS_TENSOR_QUANT8_SYMM) {
            NN_RET_CHECK(validateQuant8SymmParams(type, tag));
        } else if (type.type == ANEURALNETWORKS_TENSOR_QUANT16_ASYMM) {
            NN_RET_CHECK(validateQuant16AsymmParams(type, tag));
        } else if (type.type == ANEURALNETWORKS_TENSOR_QUANT16_SYMM) {
            NN_RET_CHECK(validateQuantSymmParams(type, tag));
        } else if (type.type == ANEURALNETWORKS_TENSOR_INT32) {
            // TODO(b/119869082): TENSOR_INT32 should not use quantization parameters.
        } else if (type.type == ANEURALNETWORKS_TENSOR_OEM_BYTE) {
            // Historically, we have allowed OEM types to use quantization parameters.
        } else {
            NN_RET_CHECK(validateNoQuantParams(type, tag));
        }
    }

    return true;
}

int validateOperandType(const ANeuralNetworksOperandType& type,
                        const Extension::OperandTypeInformation* const extensionOperandTypeInfo,
                        const char* tag, bool allowPartial) {
    return validateOperandTypeHelper(type, extensionOperandTypeInfo, tag, allowPartial)
                   ? ANEURALNETWORKS_NO_ERROR
                   : ANEURALNETWORKS_BAD_DATA;
}

int validateOperandList(uint32_t count, const uint32_t* list, uint32_t operandCount,
                        const char* tag) {
    for (uint32_t i = 0; i < count; i++) {
        if (list[i] >= operandCount) {
            LOG(ERROR) << tag << " invalid operand index at " << i << " = " << list[i]
                       << ", operandCount " << operandCount;
            return ANEURALNETWORKS_BAD_DATA;
        }
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int validateOperationOperandTypes(const std::vector<Operand>& operands, uint32_t inOperandCount,
                                  const uint32_t* inOperandIndexes,
                                  const std::vector<OperandType>& inExpectedTypes,
                                  uint32_t outOperandCount, const uint32_t* outOperandIndexes,
                                  const std::vector<OperandType>& outExpectedInTypes) {
    if (inOperandCount != static_cast<uint32_t>(inExpectedTypes.size()) ||
        outOperandCount != static_cast<uint32_t>(outExpectedInTypes.size())) {
        LOG(ERROR) << "Wrong operand count: expected " << inExpectedTypes.size() << " inputs and "
                   << outExpectedInTypes.size() << " outputs,"
                   << "got " << inOperandCount << " inputs and " << outOperandCount << " outputs";
        return ANEURALNETWORKS_BAD_DATA;
    }
    for (uint32_t i = 0; i < inOperandCount; i++) {
        if (operands[inOperandIndexes[i]].type != inExpectedTypes[i]) {
            LOG(ERROR) << "Invalid input tensor type " << operands[inOperandIndexes[i]].type
                       << " for input " << i << ", expected " << inExpectedTypes[i];
            return ANEURALNETWORKS_BAD_DATA;
        }
    }
    for (uint32_t i = 0; i < outOperandCount; i++) {
        if (operands[outOperandIndexes[i]].type != outExpectedInTypes[i]) {
            LOG(ERROR) << "Invalid output tensor type " << operands[outOperandIndexes[i]].type
                       << " for input " << i << ", expected " << outExpectedInTypes[i];
            return ANEURALNETWORKS_BAD_DATA;
        }
    }

    return ANEURALNETWORKS_NO_ERROR;
}

static int validateHalVersion(ANeuralNetworksOperationType opType, HalVersion halVersion,
                              HalVersion minSupportedHalVersion) {
    if (halVersion < minSupportedHalVersion) {
        LOG(ERROR) << "The given inputs and outputs for operation " << opType
                   << " are only supported in " << minSupportedHalVersion
                   << " and later (validating using " << halVersion << ")";
        return ANEURALNETWORKS_BAD_DATA;
    }
    return ANEURALNETWORKS_NO_ERROR;
}

// Checks if two operands have the same types, ranks (if specified), dimensions
// (if specified), scales, zeroPoints, and extraParams.
static bool compatible(const Operand& a, const Operand& b) {
    NN_RET_CHECK(a.type == b.type) << a.type << " != " << b.type;
    if (a.dimensions.size() != 0 && b.dimensions.size() != 0) {
        NN_RET_CHECK_EQ(a.dimensions.size(), b.dimensions.size()) << "Incompatible dimensions";
        for (uint32_t i = 0, n = a.dimensions.size(); i < n; ++i) {
            if (a.dimensions[i] != 0 && b.dimensions[i] != 0) {
                NN_RET_CHECK_EQ(a.dimensions[i], b.dimensions[i]) << "Incompatible dimensions";
            }
        }
    }
    NN_RET_CHECK_EQ(a.scale, b.scale);
    NN_RET_CHECK_EQ(a.zeroPoint, b.zeroPoint);
    NN_RET_CHECK(a.extraParams == b.extraParams) << a.extraParams << " != " << b.extraParams;
    return true;
}

static bool validateConditionOperand(const Operand& operand) {
    NN_RET_CHECK(operand.type == OperandType::TENSOR_BOOL8)
            << "Unexpected condition operand type: " << operand.type;
    NN_RET_CHECK_EQ(operand.dimensions.size(), 1u) << "Condition operand must be a singleton";
    NN_RET_CHECK_EQ(operand.dimensions[0], 1u) << "Condition operand must be a singleton";
    return true;
}

static void checkSubgraphValidationHelper(const SubgraphValidationHelper& helper) {
    CHECK(helper.isValidSubgraphReference != nullptr);
    CHECK(helper.getSubgraphInputCount != nullptr);
    CHECK(helper.getSubgraphOutputCount != nullptr);
    CHECK(helper.getSubgraphInputOperand != nullptr);
    CHECK(helper.getSubgraphOutputOperand != nullptr);
}

static bool validateIfOperation(uint32_t inputCount, const uint32_t* inputs, uint32_t outputCount,
                                const uint32_t* outputs, const std::vector<Operand>& operands,
                                const SubgraphValidationHelper& helper) {
    namespace op = operation_if;
    checkSubgraphValidationHelper(helper);
    NN_RET_CHECK_GE(inputCount, 3u) << "ANEURALNETWORKS_IF must have at least 3 inputs";
    NN_RET_CHECK_GE(outputCount, 1u) << "ANEURALNETWORKS_IF must have at least 1 output";
    auto validateBranchOperand = [&](const Operand& branchModelOperand) -> bool {
        NN_RET_CHECK(helper.isValidSubgraphReference(branchModelOperand))
                << "Operand is not a valid subgraph reference";
        const uint32_t branchModelInputCount = helper.getSubgraphInputCount(branchModelOperand);
        const uint32_t branchModelOutputCount = helper.getSubgraphOutputCount(branchModelOperand);
        NN_RET_CHECK_EQ(inputCount, op::kFirstInput + branchModelInputCount);
        NN_RET_CHECK_EQ(outputCount, branchModelOutputCount);
        for (uint32_t i = 0; i < branchModelInputCount; ++i) {
            const Operand& innerOperand = *helper.getSubgraphInputOperand(branchModelOperand, i);
            const Operand& outerOperand = operands[inputs[op::kFirstInput + i]];
            NN_RET_CHECK(compatible(innerOperand, outerOperand));
        }
        for (uint32_t i = 0; i < branchModelOutputCount; ++i) {
            const Operand& innerOperand = *helper.getSubgraphOutputOperand(branchModelOperand, i);
            const Operand& outerOperand = operands[outputs[i]];
            NN_RET_CHECK(compatible(innerOperand, outerOperand));
        }
        return true;
    };
    NN_RET_CHECK(validateConditionOperand(operands[inputs[op::kCondBoolOperand]]))
            << "Validation failed for IF condition operand";
    NN_RET_CHECK(validateBranchOperand(operands[inputs[op::kThenModelOperand]]))
            << "Validation failed for IF then model";
    NN_RET_CHECK(validateBranchOperand(operands[inputs[op::kElseModelOperand]]))
            << "Validation failed for IF else model";
    return true;
}

static bool validateControlFlowOperandUnknownSize(const SubgraphValidationHelper& helper,
                                                  const Operand& operand) {
    if (!helper.allowControlFlowOperationWithOperandOfUnknownSize && !isExtension(operand.type)) {
        NN_RET_CHECK_NE(nonExtensionOperandSizeOfData(operand.type, operand.dimensions), 0u);
    }
    return true;
}

static bool validateWhileOperation(uint32_t inputCount, const uint32_t* inputs,
                                   uint32_t outputCount, const uint32_t* outputs,
                                   const std::vector<Operand>& operands,
                                   const SubgraphValidationHelper& helper) {
    // Let the loop have
    // - m >= 1 input-output operands,
    // - k >= 0 state-only operands, and
    // - n >= 0 input-only operands.
    // Then
    // - the WHILE loop operation has (2 + m + k + n) inputs and m outputs.
    // - the condition model has (m + k + n) inputs and 1 output.
    // - the body model has (m + k + n) inputs and (m + k) outputs.
    namespace op = operation_while;
    checkSubgraphValidationHelper(helper);
    NN_RET_CHECK_GE(inputCount, 3u) << "ANEURALNETWORKS_WHILE must have at least 3 inputs";
    NN_RET_CHECK_GE(outputCount, 1u) << "ANEURALNETWORKS_WHILE must have at least 1 output";
    auto validateCondOperand = [&](const Operand& condModelOperand) -> bool {
        NN_RET_CHECK(helper.isValidSubgraphReference(condModelOperand))
                << "Operand is not a valid subgraph reference";
        const uint32_t condModelInputCount = helper.getSubgraphInputCount(condModelOperand);
        const uint32_t condModelOutputCount = helper.getSubgraphOutputCount(condModelOperand);
        NN_RET_CHECK_EQ(inputCount, op::kFirstInput + condModelInputCount);
        NN_RET_CHECK_EQ(condModelOutputCount, 1u);
        for (uint32_t i = 0; i < condModelInputCount; ++i) {
            const Operand& innerOperand = *helper.getSubgraphInputOperand(condModelOperand, i);
            const Operand& outerOperand = operands[inputs[op::kFirstInput + i]];
            NN_RET_CHECK(compatible(innerOperand, outerOperand));
            NN_RET_CHECK(validateControlFlowOperandUnknownSize(helper, innerOperand));
            NN_RET_CHECK(validateControlFlowOperandUnknownSize(helper, outerOperand));
        }
        NN_RET_CHECK(
                validateConditionOperand(*helper.getSubgraphOutputOperand(condModelOperand, 0)));
        return true;
    };
    auto validateBodyOperand = [&](const Operand& bodyModelOperand) -> bool {
        NN_RET_CHECK(helper.isValidSubgraphReference(bodyModelOperand))
                << "Operand is not a valid subgraph reference";
        const uint32_t bodyModelInputCount = helper.getSubgraphInputCount(bodyModelOperand);
        const uint32_t bodyModelOutputCount = helper.getSubgraphOutputCount(bodyModelOperand);
        NN_RET_CHECK_EQ(inputCount, op::kFirstInput + bodyModelInputCount);
        NN_RET_CHECK_GE(bodyModelOutputCount, outputCount);
        NN_RET_CHECK_GE(bodyModelInputCount, bodyModelOutputCount);
        const uint32_t inputOutputCount = outputCount;
        const uint32_t stateOnlyCount = bodyModelOutputCount - inputOutputCount;
        const uint32_t inputOnlyCount = bodyModelInputCount - bodyModelOutputCount;
        for (uint32_t i = 0, n = inputOutputCount + stateOnlyCount + inputOnlyCount; i < n; ++i) {
            const Operand& innerOperand = *helper.getSubgraphInputOperand(bodyModelOperand, i);
            const Operand& outerOperand = operands[inputs[op::kFirstInput + i]];
            NN_RET_CHECK(compatible(innerOperand, outerOperand));
            NN_RET_CHECK(validateControlFlowOperandUnknownSize(helper, innerOperand));
            NN_RET_CHECK(validateControlFlowOperandUnknownSize(helper, outerOperand));
        }
        for (uint32_t i = 0; i < inputOutputCount; ++i) {
            const Operand& innerOperand = *helper.getSubgraphOutputOperand(bodyModelOperand, i);
            const Operand& outerOperand = operands[outputs[i]];
            NN_RET_CHECK(compatible(innerOperand, outerOperand));
            NN_RET_CHECK(validateControlFlowOperandUnknownSize(helper, outerOperand));
        }
        for (uint32_t i = 0, n = inputOutputCount + stateOnlyCount; i < n; ++i) {
            const Operand& inputOperand = *helper.getSubgraphInputOperand(bodyModelOperand, i);
            const Operand& outputOperand = *helper.getSubgraphOutputOperand(bodyModelOperand, i);
            NN_RET_CHECK(compatible(inputOperand, outputOperand));
            NN_RET_CHECK(validateControlFlowOperandUnknownSize(helper, outputOperand));
        }
        return true;
    };
    NN_RET_CHECK(validateCondOperand(operands[inputs[op::kCondModelOperand]]))
            << "Validation failed for WHILE condition model";
    NN_RET_CHECK(validateBodyOperand(operands[inputs[op::kBodyModelOperand]]))
            << "Validation failed for WHILE body model";
    return true;
}

int validateOperation(ANeuralNetworksOperationType opType, uint32_t inputCount,
                      const uint32_t* inputIndexes, uint32_t outputCount,
                      const uint32_t* outputIndexes, const std::vector<Operand>& operands,
                      HalVersion halVersion, const SubgraphValidationHelper& helper) {
    NN_RETURN_IF_ERROR(validateOperandList(inputCount, inputIndexes,
                                           static_cast<uint32_t>(operands.size()),
                                           "ANeuralNetworksModel_addOperation inputs"));
    NN_RETURN_IF_ERROR(validateOperandList(outputCount, outputIndexes,
                                           static_cast<uint32_t>(operands.size()),
                                           "ANeuralNetworksModel_addOperation outputs"));

    if (isExtensionOperationType(opType)) {
        if (halVersion < HalVersion::V1_2) {
            LOG(ERROR)
                    << "Extension operations are supported since HAL version 1.2, validating using "
                    << halVersion;
            return ANEURALNETWORKS_BAD_DATA;
        }
        // There is no other validation we can do for an extension operation.
        return ANEURALNETWORKS_NO_ERROR;
    }

    auto logInvalidInOutNumber = [opType, inputCount, outputCount](int expIn, int expOut) {
        LOG(ERROR) << "Invalid number of input operands (" << inputCount << ", expected " << expIn
                   << ") or output operands (" << outputCount << ", expected " << expOut
                   << ") for operation " << opType;
    };

    switch (opType) {
        case ANEURALNETWORKS_OEM_OPERATION: {
            return ANEURALNETWORKS_NO_ERROR;
        }
        case ANEURALNETWORKS_RESHAPE: {
            if (inputCount != 2 || outputCount != 1) {
                logInvalidInOutNumber(2, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
                inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::TENSOR_INT32};
                outExpectedTypes = {OperandType::TENSOR_FLOAT32};
            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::TENSOR_INT32};
                outExpectedTypes = {OperandType::TENSOR_FLOAT16};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
                inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_INT32};
                outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
                inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                                   OperandType::TENSOR_INT32};
                outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED};
            } else if (inputType == OperandType::TENSOR_INT32) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::AIDL_V2));
                inExpectedTypes = {OperandType::TENSOR_INT32, OperandType::TENSOR_INT32};
                outExpectedTypes = {OperandType::TENSOR_INT32};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            const auto inputRank = operands[inputIndexes[0]].dimensions.size();
            if (inputRank > 4) {
                LOG(ERROR) << "Unsupported input tensor rank for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_DEPTH_TO_SPACE: {
            if ((inputCount != 3 && inputCount != 2) || outputCount != 1) {
                LOG(ERROR) << "Invalid number of input operands (" << inputCount
                           << ", expected 3 or 2) or output operands (" << outputCount
                           << ", expected 1) for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
                inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_FLOAT32};
            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_FLOAT16};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
                inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM, OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
                inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED, OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            if (inputCount == 3) {
                inExpectedTypes.push_back(OperandType::BOOL);
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_SPACE_TO_DEPTH: {
            if ((inputCount != 3 && inputCount != 2) || outputCount != 1) {
                LOG(ERROR) << "Invalid number of input operands (" << inputCount
                           << ", expected 3 or 2) or output operands (" << outputCount
                           << ", expected 1) for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
                inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_FLOAT32};
            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_FLOAT16};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
                inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM, OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
                inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED, OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            if (inputCount == 3) {
                inExpectedTypes.push_back(OperandType::BOOL);
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_EMBEDDING_LOOKUP: {
            if (inputCount != 2 || outputCount != 1) {
                logInvalidInOutNumber(2, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[1]].type;
            if (inputType != OperandType::TENSOR_FLOAT16 &&
                inputType != OperandType::TENSOR_FLOAT32 &&
                inputType != OperandType::TENSOR_INT32 &&
                inputType != OperandType::TENSOR_QUANT8_ASYMM &&
                inputType != OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            std::vector<OperandType> inExpectedTypes = {OperandType::TENSOR_INT32, inputType};
            std::vector<OperandType> outExpectedTypes = {inputType};
            if (inputType == OperandType::TENSOR_FLOAT16 ||
                inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            } else if (inputType == OperandType::TENSOR_INT32 ||
                       inputType == OperandType::TENSOR_QUANT8_ASYMM) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_HASHTABLE_LOOKUP: {
            if (inputCount != 3 || outputCount != 2) {
                logInvalidInOutNumber(3, 2);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[2]].type;
            if (inputType != OperandType::TENSOR_FLOAT32 &&
                inputType != OperandType::TENSOR_INT32 &&
                inputType != OperandType::TENSOR_QUANT8_ASYMM) {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            std::vector<OperandType> inExpectedTypes = {OperandType::TENSOR_INT32,
                                                        OperandType::TENSOR_INT32, inputType};
            std::vector<OperandType> outExpectedTypes = {inputType,
                                                         OperandType::TENSOR_QUANT8_ASYMM};
            NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_LSH_PROJECTION: {
            if (inputCount != 4 || outputCount != 1) {
                logInvalidInOutNumber(4, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[1]].type;
            if (inputType != OperandType::TENSOR_FLOAT16 &&
                inputType != OperandType::TENSOR_FLOAT32 &&
                inputType != OperandType::TENSOR_INT32 &&
                inputType != OperandType::TENSOR_QUANT8_ASYMM) {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto hashType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            if (hashType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT16,
                        inputType,
                        OperandType::TENSOR_FLOAT16,
                        OperandType::INT32,
                };
            } else if (hashType == OperandType::TENSOR_FLOAT32) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT32,
                        inputType,
                        OperandType::TENSOR_FLOAT32,
                        OperandType::INT32,
                };
            } else {
                LOG(ERROR) << "Unsupported hash tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            std::vector<OperandType> outExpectedTypes = {OperandType::TENSOR_INT32};
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_BIDIRECTIONAL_SEQUENCE_LSTM: {
            const uint32_t kNumOutputs = 2;
            const uint32_t kNumOutputsMerged = 1;
            const uint32_t kNumOutputsWithState = 6;
            const uint32_t kNumOutputsMergedWithState = 5;
            if (inputCount != 61 ||
                (outputCount != kNumOutputs && outputCount != kNumOutputsMerged &&
                 outputCount != kNumOutputsWithState &&
                 outputCount != kNumOutputsMergedWithState)) {
                LOG(ERROR) << "Invalid number of input operands (" << inputCount
                           << ", expected 61) or output operands (" << outputCount
                           << ", expected 1, 2, 5 or 6) for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }

            std::vector<OperandType> inExpectedTypes;
            auto inputType = operands[inputIndexes[0]].type;
            if (inputType != OperandType::TENSOR_FLOAT32 &&
                inputType != OperandType::TENSOR_FLOAT16) {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }

            inExpectedTypes = {};
            for (int i = 0; i < 48; ++i) {
                inExpectedTypes.push_back(inputType);
            }
            inExpectedTypes.push_back(OperandType::INT32);
            inExpectedTypes.push_back(inputType == OperandType::TENSOR_FLOAT32
                                              ? OperandType::FLOAT32
                                              : OperandType::FLOAT16);
            inExpectedTypes.push_back(inputType == OperandType::TENSOR_FLOAT32
                                              ? OperandType::FLOAT32
                                              : OperandType::FLOAT16);
            inExpectedTypes.push_back(OperandType::BOOL);
            inExpectedTypes.push_back(OperandType::BOOL);
            for (int i = 0; i < 8; ++i) {
                inExpectedTypes.push_back(inputType);
            }

            HalVersion minSupportedHalVersion = HalVersion::V1_2;
            if (outputCount == kNumOutputsWithState || outputCount == kNumOutputsMergedWithState) {
                minSupportedHalVersion = HalVersion::V1_3;
            }
            NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, minSupportedHalVersion));
            std::vector<OperandType> outExpectedTypes(outputCount, inputType);
            auto status = validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                        inExpectedTypes, outputCount, outputIndexes,
                                                        outExpectedTypes);
            return status;
        }
        case ANEURALNETWORKS_LSTM: {
            if ((inputCount != 23 && inputCount != 27) || outputCount != 4) {
                LOG(ERROR) << "Invalid number of input operands (" << inputCount
                           << ", expected 23 or 27) or output operands (" << outputCount
                           << ", expected 4) for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            auto inputType = operands[inputIndexes[0]].type;
            if (inputType != OperandType::TENSOR_FLOAT32 &&
                inputType != OperandType::TENSOR_FLOAT16) {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }

            inExpectedTypes = {inputType,         inputType, inputType, inputType, inputType,
                               inputType,         inputType, inputType, inputType, inputType,
                               inputType,         inputType, inputType, inputType, inputType,
                               inputType,         inputType, inputType, inputType, inputType,
                               OperandType::INT32};
            if (inputType == OperandType::TENSOR_FLOAT32) {
                inExpectedTypes.push_back(OperandType::FLOAT32);
                inExpectedTypes.push_back(OperandType::FLOAT32);
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes.push_back(OperandType::FLOAT16);
                inExpectedTypes.push_back(OperandType::FLOAT16);
            }

            outExpectedTypes = {inputType, inputType, inputType, inputType};
            if (inputCount == 23) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                for (int i = 0; i < 4; ++i) {
                    inExpectedTypes.push_back(inputType);
                }
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_QUANTIZED_16BIT_LSTM: {
            if (inputCount != 15 || outputCount != 2) {
                logInvalidInOutNumber(15, 2);
                return ANEURALNETWORKS_BAD_DATA;
            }
            NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            std::vector<OperandType> inExpectedTypes = {
                    OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_QUANT8_ASYMM,
                    OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_QUANT8_ASYMM,
                    OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_QUANT8_ASYMM,
                    OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_QUANT8_ASYMM,
                    OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_INT32,
                    OperandType::TENSOR_INT32,        OperandType::TENSOR_INT32,
                    OperandType::TENSOR_INT32,        OperandType::TENSOR_QUANT16_SYMM,
                    OperandType::TENSOR_QUANT8_ASYMM};
            std::vector<OperandType> outExpectedTypes = {OperandType::TENSOR_QUANT16_SYMM,
                                                         OperandType::TENSOR_QUANT8_ASYMM};
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_RANDOM_MULTINOMIAL: {
            if (inputCount != 3 || outputCount != 1) {
                logInvalidInOutNumber(3, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            OperandType inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT32 ||
                inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {
                        inputType,
                        OperandType::INT32,
                        OperandType::TENSOR_INT32,
                };
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            std::vector<OperandType> outExpectedTypes = {OperandType::TENSOR_INT32};
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_RNN: {
            if (inputCount != 6 || outputCount != 2) {
                logInvalidInOutNumber(6, 2);
                return ANEURALNETWORKS_BAD_DATA;
            }
            OperandType inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                        OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                        OperandType::TENSOR_FLOAT32, OperandType::INT32,
                };
                outExpectedTypes = {
                        OperandType::TENSOR_FLOAT32,
                        OperandType::TENSOR_FLOAT32,
                };
            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                        OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                        OperandType::TENSOR_FLOAT16, OperandType::INT32,
                };
                outExpectedTypes = {
                        OperandType::TENSOR_FLOAT16,
                        OperandType::TENSOR_FLOAT16,
                };
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_SVDF: {
            if (inputCount != 7 || outputCount != 2) {
                logInvalidInOutNumber(7, 2);
                return ANEURALNETWORKS_BAD_DATA;
            }
            OperandType inputType = operands[inputIndexes[0]].type;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_0));

            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            std::vector<OperandType> inExpectedTypes = {
                    inputType, inputType,          inputType,          inputType,
                    inputType, OperandType::INT32, OperandType::INT32,
            };
            std::vector<OperandType> outExpectedTypes = {inputType, inputType};
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_BATCH_TO_SPACE_ND: {
            if ((inputCount != 3 && inputCount != 2) || outputCount != 1) {
                LOG(ERROR) << "Invalid number of input operands (" << inputCount
                           << ", expected 3 or 2) or output operands (" << outputCount
                           << ", expected 1) for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT32,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {OperandType::TENSOR_FLOAT32};
            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT16,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {OperandType::TENSOR_FLOAT16};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
                inExpectedTypes = {
                        OperandType::TENSOR_QUANT8_ASYMM,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
                inExpectedTypes = {
                        OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            if (inputCount == 3) {
                inExpectedTypes.push_back(OperandType::BOOL);
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_1));
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_SPACE_TO_BATCH_ND: {
            if ((inputCount != 4 && inputCount != 3) || outputCount != 1) {
                LOG(ERROR) << "Invalid number of input operands (" << inputCount
                           << ", expected 4 or 3) or output operands (" << outputCount
                           << ", expected 1) for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT32,
                        OperandType::TENSOR_INT32,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {OperandType::TENSOR_FLOAT32};
            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT16,
                        OperandType::TENSOR_INT32,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {OperandType::TENSOR_FLOAT16};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
                if (operands[inputIndexes[0]].zeroPoint != 0) {
                    NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                }
                inExpectedTypes = {
                        OperandType::TENSOR_QUANT8_ASYMM,
                        OperandType::TENSOR_INT32,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
                inExpectedTypes = {
                        OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                        OperandType::TENSOR_INT32,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            if (inputCount == 4) {
                inExpectedTypes.push_back(OperandType::BOOL);
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_1));
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_PAD: {
            if (inputCount != 2 || outputCount != 1) {
                logInvalidInOutNumber(2, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_1));
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT32,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {OperandType::TENSOR_FLOAT32};
            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT16,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {OperandType::TENSOR_FLOAT16};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                       inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                    NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
                } else {
                    if (operands[inputIndexes[0]].zeroPoint == 0) {
                        NN_RETURN_IF_ERROR(
                                validateHalVersion(opType, halVersion, HalVersion::V1_1));
                    } else {
                        NN_RETURN_IF_ERROR(
                                validateHalVersion(opType, halVersion, HalVersion::V1_2));
                    }
                }
                inExpectedTypes = {
                        inputType,
                        OperandType::TENSOR_INT32,
                };
                outExpectedTypes = {inputType};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            const auto inputRank = operands[inputIndexes[0]].dimensions.size();
            if (inputRank > 4) {
                LOG(ERROR) << "Unsupported input tensor rank for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_PAD_V2: {
            if (inputCount != 3 || outputCount != 1) {
                logInvalidInOutNumber(3, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT32,
                        OperandType::TENSOR_INT32,
                        OperandType::FLOAT32,
                };
                outExpectedTypes = {OperandType::TENSOR_FLOAT32};
            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                inExpectedTypes = {
                        OperandType::TENSOR_FLOAT16,
                        OperandType::TENSOR_INT32,
                        OperandType::FLOAT16,
                };
                outExpectedTypes = {OperandType::TENSOR_FLOAT16};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                       inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                    NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
                } else {
                    NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
                }
                inExpectedTypes = {
                        inputType,
                        OperandType::TENSOR_INT32,
                        OperandType::INT32,
                };  // TODO(b/116699425): Make it UINT8.
                outExpectedTypes = {inputType};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            const auto inputRank = operands[inputIndexes[0]].dimensions.size();
            if (inputRank > 4) {
                LOG(ERROR) << "Unsupported input tensor rank for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_CAST: {
            if (inputCount != 1 || outputCount != 1) {
                logInvalidInOutNumber(1, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputOperand = operands[inputIndexes[0]];
            auto outputOperand = operands[outputIndexes[0]];
            auto inputType = inputOperand.type;
            auto outputType = outputOperand.type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if ((inputType == OperandType::TENSOR_FLOAT16 ||
                 inputType == OperandType::TENSOR_FLOAT32 ||
                 inputType == OperandType::TENSOR_INT32 ||
                 inputType == OperandType::TENSOR_QUANT8_ASYMM) &&
                (outputType == OperandType::TENSOR_FLOAT16 ||
                 outputType == OperandType::TENSOR_FLOAT32 ||
                 outputType == OperandType::TENSOR_INT32 ||
                 outputType == OperandType::TENSOR_QUANT8_ASYMM)) {
                inExpectedTypes = {inputType};
                outExpectedTypes = {outputType};
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            } else if (inputType == OperandType::TENSOR_BOOL8 ||
                       inputType == OperandType::TENSOR_QUANT16_ASYMM ||
                       inputType == OperandType::TENSOR_QUANT16_SYMM ||
                       inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED ||
                       inputType == OperandType::TENSOR_QUANT8_SYMM) {
                inExpectedTypes = {inputType};
                outExpectedTypes = {inputType};  // Only identity CAST is supported.
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            } else {
                LOG(ERROR) << "Unsupported data type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            // Validate that output shape is equal to input shape if dimensions
            // are already known.
            auto getNumberOfElements = [](const std::vector<uint32_t>& dims) {
                if (dims.size() == 0) {
                    return 0;
                }
                return std::accumulate(dims.begin(), dims.end(), 1, std::multiplies<>());
            };
            if (inputOperand.dimensions.size() != 0 && outputOperand.dimensions.size() != 0 &&
                getNumberOfElements(outputOperand.dimensions) != 0 &&
                inputOperand.dimensions != outputOperand.dimensions) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_MEAN: {
            if (inputCount != 3 || outputCount != 1) {
                logInvalidInOutNumber(3, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            const auto inputRank = operands[inputIndexes[0]].dimensions.size();
            if (inputRank > 4) {
                LOG(ERROR) << "Unsupported input tensor rank for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_1));
            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_1));
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            std::vector<OperandType> inExpectedTypes = {inputType, OperandType::TENSOR_INT32,
                                                        OperandType::INT32};
            std::vector<OperandType> outExpectedTypes = {inputType};
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_ARGMAX:
        case ANEURALNETWORKS_ARGMIN: {
            if (inputCount != 2 || outputCount != 1) {
                logInvalidInOutNumber(2, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT16 ||
                inputType == OperandType::TENSOR_FLOAT32 ||
                inputType == OperandType::TENSOR_INT32 ||
                inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                inExpectedTypes = {inputType, OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_INT32};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_EXPAND_DIMS: {
            if (inputCount != 2 || outputCount != 1) {
                logInvalidInOutNumber(2, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT16 ||
                inputType == OperandType::TENSOR_FLOAT32 ||
                inputType == OperandType::TENSOR_INT32 ||
                inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                inExpectedTypes = {inputType, OperandType::INT32};
                outExpectedTypes = {inputType};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_SPLIT: {
            if (inputCount != 3) {
                LOG(ERROR) << "Invalid number of input operands (" << inputCount << ", expected 3)"
                           << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            if (inputType != OperandType::TENSOR_FLOAT16 &&
                inputType != OperandType::TENSOR_FLOAT32 &&
                inputType != OperandType::TENSOR_INT32 &&
                inputType != OperandType::TENSOR_QUANT8_ASYMM &&
                inputType != OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            }
            std::vector<OperandType> inExpectedTypes = {inputType, OperandType::INT32,
                                                        OperandType::INT32};
            std::vector<OperandType> outExpectedTypes(outputCount, inputType);
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_MAXIMUM:
        case ANEURALNETWORKS_MINIMUM: {
            if (inputCount != 2 || outputCount != 1) {
                logInvalidInOutNumber(2, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            OperandType inputType = operands[inputIndexes[0]].type;
            if (inputType == OperandType::TENSOR_FLOAT16 ||
                inputType == OperandType::TENSOR_FLOAT32 ||
                inputType == OperandType::TENSOR_INT32 ||
                inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                inExpectedTypes = {inputType, inputType};
                outExpectedTypes = {inputType};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_GROUPED_CONV_2D: {
            if ((inputCount != 12 && inputCount != 9) || outputCount != 1) {
                LOG(ERROR) << "Invalid number of input operands (" << inputCount
                           << ", expected 12 or 9) or output operands (" << outputCount
                           << ", expected 1) for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            auto filterType = operands[inputIndexes[1]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT32) {
                inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::TENSOR_FLOAT32,
                                   OperandType::TENSOR_FLOAT32, OperandType::INT32,
                                   OperandType::INT32,          OperandType::INT32,
                                   OperandType::INT32,          OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_FLOAT32};
            } else if (inputType == OperandType::TENSOR_FLOAT16) {
                inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::TENSOR_FLOAT16,
                                   OperandType::TENSOR_FLOAT16, OperandType::INT32,
                                   OperandType::INT32,          OperandType::INT32,
                                   OperandType::INT32,          OperandType::INT32};
                outExpectedTypes = {OperandType::TENSOR_FLOAT16};
            } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                       inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                if (filterType != inputType &&
                    filterType != OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL) {
                    LOG(ERROR) << "Unsupported filter tensor type for operation " << opType;
                    return ANEURALNETWORKS_BAD_DATA;
                }

                if (filterType == OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL &&
                    std::get<Operand::SymmPerChannelQuantParams>(
                            operands[inputIndexes[1]].extraParams)
                                    .channelDim != 0) {
                    LOG(ERROR) << "Unsupported filter tensor channel dimension for operation "
                               << opType;
                    return ANEURALNETWORKS_BAD_DATA;
                }

                inExpectedTypes = {
                        inputType,          filterType,         OperandType::TENSOR_INT32,
                        OperandType::INT32, OperandType::INT32, OperandType::INT32,
                        OperandType::INT32, OperandType::INT32};
                outExpectedTypes = {inputType};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }

            if (inputCount == 12) {
                std::vector<OperandType> explicitScalarTypes(3, OperandType::INT32);
                inExpectedTypes.insert(inExpectedTypes.end(), explicitScalarTypes.begin(),
                                       explicitScalarTypes.end());
            }
            inExpectedTypes.push_back(OperandType::BOOL);
            if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_TILE: {
            if (inputCount != 2 || outputCount != 1) {
                logInvalidInOutNumber(2, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT16 ||
                inputType == OperandType::TENSOR_FLOAT32 ||
                inputType == OperandType::TENSOR_INT32 ||
                inputType == OperandType::TENSOR_QUANT8_ASYMM ||
                inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                inExpectedTypes = {inputType, OperandType::TENSOR_INT32};
                outExpectedTypes = {inputType};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_POW: {
            if (inputCount != 2 || outputCount != 1) {
                logInvalidInOutNumber(2, 1);
                return ANEURALNETWORKS_BAD_DATA;
            }
            auto inputType = operands[inputIndexes[0]].type;
            std::vector<OperandType> inExpectedTypes;
            std::vector<OperandType> outExpectedTypes;
            if (inputType == OperandType::TENSOR_FLOAT16 ||
                inputType == OperandType::TENSOR_FLOAT32) {
                inExpectedTypes = {inputType, inputType};
                outExpectedTypes = {inputType};
            } else {
                LOG(ERROR) << "Unsupported input tensor type for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            } else {
                NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_2));
            }
            return validateOperationOperandTypes(operands, inputCount, inputIndexes,
                                                 inExpectedTypes, outputCount, outputIndexes,
                                                 outExpectedTypes);
        }
        case ANEURALNETWORKS_IF: {
            NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            return validateIfOperation(inputCount, inputIndexes, outputCount, outputIndexes,
                                       operands, helper)
                           ? ANEURALNETWORKS_NO_ERROR
                           : ANEURALNETWORKS_BAD_DATA;
        }
        case ANEURALNETWORKS_WHILE: {
            NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
            return validateWhileOperation(inputCount, inputIndexes, outputCount, outputIndexes,
                                          operands, helper)
                           ? ANEURALNETWORKS_NO_ERROR
                           : ANEURALNETWORKS_BAD_DATA;
        }
        default: {
            const OperationRegistration* operationRegistration =
                    BuiltinOperationResolver::get()->findOperation(
                            static_cast<OperationType>(opType));
            if (operationRegistration == nullptr) {
                if (0 <= opType && opType < kNumberOfOperationTypes) {
                    LOG(ERROR) << opType << " not registered";
                } else {
                    LOG(ERROR) << "Operation type " << opType << " out of the range [0, "
                               << kNumberOfOperationTypes << ")";
                }
                return ANEURALNETWORKS_UNEXPECTED_NULL;
            }
            if (operationRegistration->validate == nullptr) {
                LOG(ERROR) << "Incomplete operation registration: " << opType;
                return ANEURALNETWORKS_UNEXPECTED_NULL;
            }
            OperationValidationContext context(operationRegistration->name, inputCount,
                                               inputIndexes, outputCount, outputIndexes,
                                               operands.data());
            const auto maybeVersion = operationRegistration->validate(&context);
            if (!maybeVersion.has_value()) {
                LOG(ERROR) << "Validation failed for operation " << opType << ": "
                           << maybeVersion.error();
                return ANEURALNETWORKS_BAD_DATA;
            }
            if (!validateVersion(&context, convert(halVersion), maybeVersion.value())) {
                LOG(ERROR) << "Validation failed for operation " << opType;
                return ANEURALNETWORKS_BAD_DATA;
            }
            return ANEURALNETWORKS_NO_ERROR;
        }
    }
}

ErrorStatus convertResultCodeToErrorStatus(int resultCode) {
    switch (resultCode) {
        case ANEURALNETWORKS_NO_ERROR:
            return ErrorStatus::NONE;

        case ANEURALNETWORKS_BAD_DATA:
        case ANEURALNETWORKS_UNEXPECTED_NULL:
            return ErrorStatus::INVALID_ARGUMENT;

        case ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE:
            return ErrorStatus::OUTPUT_INSUFFICIENT_SIZE;

        case ANEURALNETWORKS_UNAVAILABLE_DEVICE:
            return ErrorStatus::DEVICE_UNAVAILABLE;

        case ANEURALNETWORKS_BAD_STATE:
        case ANEURALNETWORKS_INCOMPLETE:
        case ANEURALNETWORKS_OP_FAILED:
        case ANEURALNETWORKS_OUT_OF_MEMORY:
        case ANEURALNETWORKS_UNMAPPABLE:
        case ANEURALNETWORKS_DEAD_OBJECT:
            return ErrorStatus::GENERAL_FAILURE;

        case ANEURALNETWORKS_MISSED_DEADLINE_TRANSIENT:
            return ErrorStatus::MISSED_DEADLINE_TRANSIENT;
        case ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT:
            return ErrorStatus::MISSED_DEADLINE_PERSISTENT;
        case ANEURALNETWORKS_RESOURCE_EXHAUSTED_TRANSIENT:
            return ErrorStatus::RESOURCE_EXHAUSTED_TRANSIENT;
        case ANEURALNETWORKS_RESOURCE_EXHAUSTED_PERSISTENT:
            return ErrorStatus::RESOURCE_EXHAUSTED_PERSISTENT;
    }
    LOG(ERROR) << "Unknown result code " << resultCode << " mapped to ErrorStatus::GENERAL_FAILURE";
    return ErrorStatus::GENERAL_FAILURE;
}

int convertErrorStatusToResultCode(ErrorStatus status) {
    switch (status) {
        case ErrorStatus::NONE:
            return ANEURALNETWORKS_NO_ERROR;
        case ErrorStatus::DEVICE_UNAVAILABLE:
            return ANEURALNETWORKS_UNAVAILABLE_DEVICE;
        case ErrorStatus::GENERAL_FAILURE:
            return ANEURALNETWORKS_OP_FAILED;
        case ErrorStatus::OUTPUT_INSUFFICIENT_SIZE:
            return ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE;
        case ErrorStatus::INVALID_ARGUMENT:
            return ANEURALNETWORKS_BAD_DATA;
        case ErrorStatus::MISSED_DEADLINE_TRANSIENT:
            return ANEURALNETWORKS_MISSED_DEADLINE_TRANSIENT;
        case ErrorStatus::MISSED_DEADLINE_PERSISTENT:
            return ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT;
        case ErrorStatus::RESOURCE_EXHAUSTED_TRANSIENT:
            return ANEURALNETWORKS_RESOURCE_EXHAUSTED_TRANSIENT;
        case ErrorStatus::RESOURCE_EXHAUSTED_PERSISTENT:
            return ANEURALNETWORKS_RESOURCE_EXHAUSTED_PERSISTENT;
        case ErrorStatus::DEAD_OBJECT:
            return ANEURALNETWORKS_DEAD_OBJECT;
    }
    LOG(ERROR) << "Unknown ErrorStatus " << status << " mapped to ANEURALNETWORKS_OP_FAILED";
    return ANEURALNETWORKS_OP_FAILED;
}

std::tuple<int, std::vector<OutputShape>, Timing> getExecutionResult(
        ErrorStatus status, std::vector<OutputShape> outputShapes, Timing timing) {
    constexpr Timing kNoTiming = {};
    const int n = convertErrorStatusToResultCode(status);
    if (status != ErrorStatus::NONE && status != ErrorStatus::OUTPUT_INSUFFICIENT_SIZE &&
        !outputShapes.empty()) {
        LOG(ERROR) << "The driver returned OutputShapes when it shouldn't.";
        outputShapes.clear();
    }
    if (status != ErrorStatus::NONE && timing != kNoTiming) {
        LOG(ERROR) << "The driver returned Timing when it shouldn't.";
        timing = kNoTiming;
    }
    return {n, std::move(outputShapes), timing};
}

FenceState syncWait(int fd, int timeout) {
    // This implementation is directly based on the ::sync_wait() implementation.

    struct pollfd fds;
    int ret;

    if (fd < 0) {
        errno = EINVAL;
        return FenceState::UNKNOWN;
    }

    fds.fd = fd;
    fds.events = POLLIN;

    do {
        ret = poll(&fds, 1, timeout);
        if (ret > 0) {
            if (fds.revents & POLLNVAL) {
                errno = EINVAL;
                return FenceState::UNKNOWN;
            }
            if (fds.revents & POLLERR) {
                errno = EINVAL;
                return FenceState::ERROR;
            }
            return FenceState::SIGNALED;
        } else if (ret == 0) {
            errno = ETIME;
            return FenceState::ACTIVE;
        }
    } while (ret == -1 && (errno == EINTR || errno == EAGAIN));

    return FenceState::UNKNOWN;
}

#ifdef NN_DEBUGGABLE
uint32_t getProp(const char* str, uint32_t defaultValue) {
    const std::string propStr = android::base::GetProperty(str, "");
    if (propStr.size() > 0) {
        return std::stoi(propStr);
    } else {
        return defaultValue;
    }
}
#endif  // NN_DEBUGGABLE

}  // namespace nn
}  // namespace android
