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

#include "LegacyHalUtils.h"

#include <nnapi/TypeUtils.h>
#include <nnapi/hal/1.0/Conversions.h>
#include <nnapi/hal/1.1/Conversions.h>
#include <nnapi/hal/1.2/Conversions.h>
#include <nnapi/hal/1.3/Conversions.h>

#include <algorithm>
#include <limits>
#include <set>
#include <string>
#include <tuple>
#include <type_traits>
#include <utility>
#include <vector>

#include "CpuExecutor.h"
#include "NeuralNetworks.h"
#include "ValidateHal.h"

namespace android {
namespace nn {

constexpr V1_0::PerformanceInfo kNoPerformanceInfo = {
        .execTime = std::numeric_limits<float>::max(),
        .powerUsage = std::numeric_limits<float>::max()};

template <typename Type>
static Type handleError(GeneralResult<Type> result) {
    CHECK(result.has_value()) << "Unhandled error (" << result.error().code
                              << "): " << result.error().message;
    return std::move(result).value();
}

LegacyOptionalTimePoint makeDeadline(const V1_3::OptionalTimePoint& timePoint) {
    using Disc = V1_3::OptionalTimePoint::hidl_discriminator;
    if (timePoint.getDiscriminator() == Disc::none) {
        return LegacyOptionalTimePoint{};
    }
    const uint64_t count = timePoint.nanosecondsSinceEpoch();
    return LegacyTimePoint{LegacyDuration{count}};
}

LegacyOptionalTimePoint makeDeadline(const V1_3::OptionalTimeoutDuration& optionalDuration) {
    if (optionalDuration.getDiscriminator() ==
        V1_3::OptionalTimeoutDuration::hidl_discriminator::none) {
        return LegacyOptionalTimePoint{};
    }

    const auto duration = LegacyDuration{optionalDuration.nanoseconds()};
    constexpr auto kMaxTime = LegacyTimePoint::max();
    const auto currentTime = LegacyClock::now();

    // If there would be an overflow, use the max value.
    if (duration > kMaxTime - currentTime) {
        return kMaxTime;
    }
    return currentTime + duration;
}

bool hasDeadlinePassed(const LegacyOptionalTimePoint& deadline) {
    if (!deadline.has_value()) {
        return false;
    }
    return LegacyClock::now() >= *deadline;
}

bool isExtensionOperandType(V1_3::OperandType type) {
    return isExtensionOperandType(static_cast<OperandType>(type));
}

bool isExtensionOperationType(V1_3::OperationType type) {
    return isExtensionOperationType(static_cast<OperationType>(type));
}

std::string getOperandTypeName(V1_3::OperandType type) {
    return toString(type);
}

std::string getOperationName(V1_3::OperationType type) {
    return toString(type);
}

uint32_t nonExtensionOperandSizeOfData(V1_3::OperandType type,
                                       const std::vector<uint32_t>& dimensions) {
    return nonExtensionOperandSizeOfData(uncheckedConvert(type), dimensions);
}

bool nonExtensionOperandSizeOfDataOverflowsUInt32(V1_3::OperandType type,
                                                  const std::vector<uint32_t>& dimensions) {
    return nonExtensionOperandSizeOfDataOverflowsUInt32(uncheckedConvert(type), dimensions);
}

bool tensorHasUnspecifiedDimensions(V1_3::OperandType type,
                                    const std::vector<uint32_t>& dimensions) {
    return tensorHasUnspecifiedDimensions(static_cast<int>(type), dimensions.data(),
                                          dimensions.size());
}

bool tensorHasUnspecifiedDimensions(const V1_3::Operand& operand) {
    return tensorHasUnspecifiedDimensions(static_cast<int>(operand.type), operand.dimensions.data(),
                                          operand.dimensions.size());
}

void logModelToInfo(const V1_0::Model& model) {
    LOG(INFO) << "V1_0::Model start";
    LOG(INFO) << "operands" << toString(model.operands);
    LOG(INFO) << "operations" << toString(model.operations);
    LOG(INFO) << "inputIndexes" << toString(model.inputIndexes);
    LOG(INFO) << "outputIndexes" << toString(model.outputIndexes);
    LOG(INFO) << "operandValues size" << model.operandValues.size();
    LOG(INFO) << "pools" << SHOW_IF_DEBUG(toString(model.pools));
}

void logModelToInfo(const V1_1::Model& model) {
    LOG(INFO) << "V1_1::Model start";
    LOG(INFO) << "operands" << toString(model.operands);
    LOG(INFO) << "operations" << toString(model.operations);
    LOG(INFO) << "inputIndexes" << toString(model.inputIndexes);
    LOG(INFO) << "outputIndexes" << toString(model.outputIndexes);
    LOG(INFO) << "operandValues size " << model.operandValues.size();
    LOG(INFO) << "pools" << SHOW_IF_DEBUG(toString(model.pools));
}

void logModelToInfo(const V1_2::Model& model) {
    LOG(INFO) << "V1_2::Model start";
    LOG(INFO) << "operands" << toString(model.operands);
    LOG(INFO) << "operations" << toString(model.operations);
    LOG(INFO) << "inputIndexes" << toString(model.inputIndexes);
    LOG(INFO) << "outputIndexes" << toString(model.outputIndexes);
    LOG(INFO) << "operandValues size" << model.operandValues.size();
    LOG(INFO) << "pools" << SHOW_IF_DEBUG(toString(model.pools));
    LOG(INFO) << "relaxComputationFloat32toFloat16" << model.relaxComputationFloat32toFloat16;
    LOG(INFO) << "extensionNameToPrefix" << toString(model.extensionNameToPrefix);
}

static void logSubgraphToInfo(std::string label, const V1_3::Subgraph& subgraph) {
    LOG(INFO) << label << ".operands" << toString(subgraph.operands);
    LOG(INFO) << label << ".operations" << toString(subgraph.operations);
    LOG(INFO) << label << ".inputIndexes" << toString(subgraph.inputIndexes);
    LOG(INFO) << label << ".outputIndexes" << toString(subgraph.outputIndexes);
}

void logModelToInfo(const V1_3::Model& model) {
    LOG(INFO) << "V1_3::Model start";
    logSubgraphToInfo("main", model.main);
    for (uint32_t i = 0, n = model.referenced.size(); i < n; ++i) {
        logSubgraphToInfo("referenced[" + std::to_string(i) + "]", model.referenced[i]);
    }
    LOG(INFO) << "operandValues size " << model.operandValues.size();
    LOG(INFO) << "pools" << SHOW_IF_DEBUG(toString(model.pools));
    LOG(INFO) << "relaxComputationFloat32toFloat16 " << model.relaxComputationFloat32toFloat16;
    LOG(INFO) << "extensionNameToPrefix" << toString(model.extensionNameToPrefix);
}

bool validateOperandSymmPerChannelQuantParams(
        const V1_3::Operand& halOperand,
        const ANeuralNetworksSymmPerChannelQuantParams& channelQuant, const char* tag) {
    if (halOperand.type != V1_3::OperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL) {
        return false;
    }

    NN_RET_CHECK_LT(channelQuant.channelDim, halOperand.dimensions.size()) << tag;
    NN_RET_CHECK(channelQuant.scales != nullptr) << tag;
    NN_RET_CHECK_EQ(channelQuant.scaleCount, halOperand.dimensions[channelQuant.channelDim]) << tag;
    NN_RET_CHECK_NE(halOperand.dimensions[channelQuant.channelDim], 0u)
            << tag << " channel dimension " << channelQuant.channelDim << " is underspecified";
    for (uint32_t i = 0; i < halOperand.dimensions[channelQuant.channelDim]; i++) {
        NN_RET_CHECK_GT(channelQuant.scales[i], 0.0f) << tag << " invalid scaleArray[" << i << "]";
    }
    return true;
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

static inline int validateOperation(ANeuralNetworksOperationType opType, uint32_t inputCount,
                                    const uint32_t* inputIndexes, uint32_t outputCount,
                                    const uint32_t* outputIndexes,
                                    const std::vector<Operand>& operands, HalVersion halVersion) {
    if (opType == ANEURALNETWORKS_IF || opType == ANEURALNETWORKS_WHILE) {
        NN_RETURN_IF_ERROR(validateHalVersion(opType, halVersion, HalVersion::V1_3));
        LOG(ERROR) << "This validateOperation() overload does not support control flow";
        return ANEURALNETWORKS_BAD_DATA;
    }
    return validateOperation(opType, inputCount, inputIndexes, outputCount, outputIndexes, operands,
                             halVersion, {});
}

V1_3::ErrorStatus convertResultCodeToHalErrorStatus(int resultCode) {
    return convertToV1_3(convertResultCodeToErrorStatus(resultCode));
}

int convertErrorStatusToResultCode(V1_3::ErrorStatus status) {
    return convertErrorStatusToResultCode(uncheckedConvert(status));
}

std::tuple<int, std::vector<OutputShape>, Timing> getExecutionResult(
        V1_3::ErrorStatus status, const hardware::hidl_vec<V1_2::OutputShape>& outputShapes,
        const V1_2::Timing& timing) {
    return getExecutionResult(uncheckedConvert(status), uncheckedConvert(outputShapes),
                              uncheckedConvert(timing));
}

// Capabilities::operandPerformance utilities.
// The field Capabilities::operandPerformance is a vector sorted by the field
// Capabilities::OperandPerformance::type.

template <HalVersion version>
hardware::hidl_vec<VersionedOperandPerformance<version>> nonExtensionOperandPerformance(
        V1_0::PerformanceInfo perf) {
    using OpPerf = VersionedOperandPerformance<version>;

    // Note: range presents enumerators in declaration order, not in numerical order.
    static constexpr hardware::hidl_enum_range<VersionedOperandType<version>> kOperandTypeRange;

    std::vector<OpPerf> ret;
    ret.reserve(kOperandTypeRange.end() - kOperandTypeRange.begin());
    for (VersionedOperandType<version> type : kOperandTypeRange) {
        if (static_cast<V1_3::OperandType>(type) != V1_3::OperandType::SUBGRAPH) {
            ret.push_back(OpPerf{type, perf});
        }
    }
    std::sort(ret.begin(), ret.end(),
              [](const OpPerf& a, const OpPerf& b) { return a.type < b.type; });

    return ret;
}

template hardware::hidl_vec<V1_2::Capabilities::OperandPerformance>
nonExtensionOperandPerformance<HalVersion::V1_2>(V1_0::PerformanceInfo perf);
template hardware::hidl_vec<V1_3::Capabilities::OperandPerformance>
nonExtensionOperandPerformance<HalVersion::V1_3>(V1_0::PerformanceInfo perf);

template <HalVersion version>
void update(hardware::hidl_vec<VersionedOperandPerformance<version>>* operandPerformance,
            VersionedOperandType<version> type, V1_0::PerformanceInfo perf) {
    CHECK(operandPerformance != nullptr);
    const auto it =
            std::lower_bound(operandPerformance->begin(), operandPerformance->end(), type,
                             [](const VersionedOperandPerformance<version>& perf,
                                VersionedOperandType<version> type) { return perf.type < type; });
    CHECK(it != operandPerformance->end())
            << toString(type) << " not in " << toString(*operandPerformance);
    it->info = perf;
}

void update(hardware::hidl_vec<V1_2::Capabilities::OperandPerformance>* operandPerformance,
            V1_2::OperandType type, V1_0::PerformanceInfo perf) {
    update<HalVersion::V1_2>(operandPerformance, type, perf);
}
void update(hardware::hidl_vec<V1_3::Capabilities::OperandPerformance>* operandPerformance,
            V1_3::OperandType type, V1_0::PerformanceInfo perf) {
    update<HalVersion::V1_3>(operandPerformance, type, perf);
}

template <HalVersion version>
V1_0::PerformanceInfo lookup(
        const hardware::hidl_vec<VersionedOperandPerformance<version>>& operandPerformance,
        VersionedOperandType<version> type) {
    const auto it = std::lower_bound(operandPerformance.begin(), operandPerformance.end(), type,
                                     [](const VersionedOperandPerformance<version>& perf,
                                        VersionedOperandType<version> type) {
                                         return static_cast<V1_3::OperandType>(perf.type) <
                                                static_cast<V1_3::OperandType>(type);
                                     });
    if (it == operandPerformance.end()) {
        LOG(WARNING) << "No PerformanceInfo for " << toString(type);
        return kNoPerformanceInfo;
    } else {
        return it->info;
    }
}

V1_0::PerformanceInfo lookup(
        const hardware::hidl_vec<V1_2::Capabilities::OperandPerformance>& operandPerformance,
        V1_2::OperandType type) {
    return lookup<HalVersion::V1_2>(operandPerformance, type);
}
V1_0::PerformanceInfo lookup(
        const hardware::hidl_vec<V1_3::Capabilities::OperandPerformance>& operandPerformance,
        V1_3::OperandType type) {
    CHECK(type != V1_3::OperandType::SUBGRAPH)
            << "Use Capabilities::ifPerformance or Capabilities::whilePerformance";
    return lookup<HalVersion::V1_3>(operandPerformance, type);
}

bool setRunTimePoolInfosFromHidlMemories(std::vector<RunTimePoolInfo>* poolInfos,
                                         const hardware::hidl_vec<hardware::hidl_memory>& pools) {
    return setRunTimePoolInfosFromCanonicalMemories(poolInfos, uncheckedConvert(pools));
}

// Versioning

// In Android P, most data types are treated as having the same performance as TENSOR_QUANT8_ASYMM.
// This array must be in sorted order.
static const V1_3::OperandType kQuantized8PerformanceConsistentWithP[] = {
        V1_3::OperandType::INT32, V1_3::OperandType::UINT32, V1_3::OperandType::TENSOR_INT32,
        V1_3::OperandType::OEM, V1_3::OperandType::TENSOR_OEM_BYTE};

static bool isQuantized8PerformanceConsistentWithP(const V1_2::Capabilities& capabilities) {
    const V1_0::PerformanceInfo quantized8Performance =
            lookup(capabilities.operandPerformance, V1_2::OperandType::TENSOR_QUANT8_ASYMM);
    return std::all_of(std::begin(kQuantized8PerformanceConsistentWithP),
                       std::end(kQuantized8PerformanceConsistentWithP),
                       [quantized8Performance, &capabilities](V1_3::OperandType type) {
                           return quantized8Performance ==
                                  lookup(capabilities.operandPerformance,
                                         static_cast<V1_2::OperandType>(type));
                       });
}

static bool isQuantized8PerformanceConsistentWithP(const V1_3::Capabilities& capabilities) {
    const V1_0::PerformanceInfo quantized8Performance =
            lookup(capabilities.operandPerformance, V1_3::OperandType::TENSOR_QUANT8_ASYMM);
    return std::all_of(std::begin(kQuantized8PerformanceConsistentWithP),
                       std::end(kQuantized8PerformanceConsistentWithP),
                       [quantized8Performance, &capabilities](V1_3::OperandType type) {
                           return quantized8Performance ==
                                  lookup(capabilities.operandPerformance, type);
                       });
}

static hardware::hidl_vec<V1_2::Capabilities::OperandPerformance>
makeQuantized8PerformanceConsistentWithP(V1_0::PerformanceInfo quantized8Performance) {
    hardware::hidl_vec<V1_2::Capabilities::OperandPerformance> ret(
            std::size(kQuantized8PerformanceConsistentWithP));
    std::transform(std::begin(kQuantized8PerformanceConsistentWithP),
                   std::end(kQuantized8PerformanceConsistentWithP), ret.begin(),
                   [quantized8Performance](
                           V1_3::OperandType type) -> V1_2::Capabilities::OperandPerformance {
                       return {static_cast<V1_2::OperandType>(type), quantized8Performance};
                   });
    return ret;
}

bool compliantWithV1_0(const V1_0::Capabilities&) {
    return true;
}

bool compliantWithV1_0(const V1_1::Capabilities& capabilities) {
    return capabilities.relaxedFloat32toFloat16Performance == capabilities.float32Performance;
}

bool compliantWithV1_0(const V1_2::Capabilities& capabilities) {
    const V1_0::PerformanceInfo perfTensorFloat32 =
            lookup(capabilities.operandPerformance, V1_2::OperandType::TENSOR_FLOAT32);
    const V1_0::PerformanceInfo perfFloat32 =
            lookup(capabilities.operandPerformance, V1_2::OperandType::FLOAT32);
    if (perfTensorFloat32 != perfFloat32 ||
        perfTensorFloat32 != capabilities.relaxedFloat32toFloat16PerformanceTensor ||
        perfFloat32 != capabilities.relaxedFloat32toFloat16PerformanceScalar) {
        return false;
    }

    return isQuantized8PerformanceConsistentWithP(capabilities);
}

bool compliantWithV1_0(const V1_3::Capabilities& capabilities) {
    const V1_0::PerformanceInfo perfTensorFloat32 =
            lookup(capabilities.operandPerformance, V1_3::OperandType::TENSOR_FLOAT32);
    const V1_0::PerformanceInfo perfFloat32 =
            lookup(capabilities.operandPerformance, V1_3::OperandType::FLOAT32);
    if (perfTensorFloat32 != perfFloat32 ||
        perfTensorFloat32 != capabilities.relaxedFloat32toFloat16PerformanceTensor ||
        perfFloat32 != capabilities.relaxedFloat32toFloat16PerformanceScalar) {
        return false;
    }

    return isQuantized8PerformanceConsistentWithP(capabilities);
}

bool compliantWithV1_1(const V1_0::Capabilities&) {
    return true;
}

bool compliantWithV1_1(const V1_1::Capabilities&) {
    return true;
}

bool compliantWithV1_1(const V1_2::Capabilities& capabilities) {
    if ((capabilities.relaxedFloat32toFloat16PerformanceTensor !=
         capabilities.relaxedFloat32toFloat16PerformanceScalar) ||
        (lookup(capabilities.operandPerformance, V1_2::OperandType::TENSOR_FLOAT32) !=
         lookup(capabilities.operandPerformance, V1_2::OperandType::FLOAT32))) {
        return false;
    }

    return isQuantized8PerformanceConsistentWithP(capabilities);
}

bool compliantWithV1_1(const V1_3::Capabilities& capabilities) {
    if ((capabilities.relaxedFloat32toFloat16PerformanceTensor !=
         capabilities.relaxedFloat32toFloat16PerformanceScalar) ||
        (lookup(capabilities.operandPerformance, V1_3::OperandType::TENSOR_FLOAT32) !=
         lookup(capabilities.operandPerformance, V1_3::OperandType::FLOAT32))) {
        return false;
    }

    return isQuantized8PerformanceConsistentWithP(capabilities);
}

bool compliantWithV1_2(const V1_0::Capabilities&) {
    return true;
}

bool compliantWithV1_2(const V1_1::Capabilities&) {
    return true;
}

bool compliantWithV1_2(const V1_2::Capabilities&) {
    return true;
}

bool compliantWithV1_2(const V1_3::Capabilities&) {
    return true;
}

bool compliantWithV1_3(const V1_0::Capabilities&) {
    return true;
}

bool compliantWithV1_3(const V1_1::Capabilities&) {
    return true;
}

bool compliantWithV1_3(const V1_2::Capabilities&) {
    return true;
}

bool compliantWithV1_3(const V1_3::Capabilities&) {
    return true;
}

V1_0::ErrorStatus convertToV1_0(V1_0::ErrorStatus status) {
    return status;
}

V1_0::ErrorStatus convertToV1_0(V1_3::ErrorStatus status) {
    switch (status) {
        case V1_3::ErrorStatus::NONE:
            return V1_0::ErrorStatus::NONE;
        case V1_3::ErrorStatus::DEVICE_UNAVAILABLE:
            return V1_0::ErrorStatus::DEVICE_UNAVAILABLE;
        case V1_3::ErrorStatus::GENERAL_FAILURE:
            return V1_0::ErrorStatus::GENERAL_FAILURE;
        case V1_3::ErrorStatus::OUTPUT_INSUFFICIENT_SIZE:
            return V1_0::ErrorStatus::OUTPUT_INSUFFICIENT_SIZE;
        case V1_3::ErrorStatus::INVALID_ARGUMENT:
            return V1_0::ErrorStatus::INVALID_ARGUMENT;
        case V1_3::ErrorStatus::MISSED_DEADLINE_TRANSIENT:
            return V1_0::ErrorStatus::GENERAL_FAILURE;
        case V1_3::ErrorStatus::MISSED_DEADLINE_PERSISTENT:
            return V1_0::ErrorStatus::GENERAL_FAILURE;
        case V1_3::ErrorStatus::RESOURCE_EXHAUSTED_TRANSIENT:
            return V1_0::ErrorStatus::GENERAL_FAILURE;
        case V1_3::ErrorStatus::RESOURCE_EXHAUSTED_PERSISTENT:
            return V1_0::ErrorStatus::GENERAL_FAILURE;
    }
    LOG(ERROR) << "Unknown ErrorStatus: " << toString(status) << " mapped to GENERAL_FAILURE";
    return V1_0::ErrorStatus::GENERAL_FAILURE;
}

V1_3::ErrorStatus convertToV1_3(V1_0::ErrorStatus status) {
    return static_cast<V1_3::ErrorStatus>(status);
}

V1_3::ErrorStatus convertToV1_3(V1_3::ErrorStatus status) {
    return status;
}

static V1_0::OperationType uncheckedConvertToV1_0(V1_1::OperationType type) {
    return static_cast<V1_0::OperationType>(type);
}

static V1_0::OperationType uncheckedConvertToV1_0(V1_2::OperationType type) {
    return static_cast<V1_0::OperationType>(type);
}

V1_0::OperationType uncheckedConvertToV1_0(V1_3::OperationType type) {
    return static_cast<V1_0::OperationType>(type);
}

static V1_1::OperationType convertToV1_1(V1_0::OperationType type) {
    return static_cast<V1_1::OperationType>(type);
}

static V1_1::OperationType uncheckedConvertToV1_1(V1_2::OperationType type) {
    return static_cast<V1_1::OperationType>(type);
}

V1_1::OperationType uncheckedConvertToV1_1(V1_3::OperationType type) {
    return static_cast<V1_1::OperationType>(type);
}

static V1_2::OperationType convertToV1_2(V1_0::OperationType type) {
    return static_cast<V1_2::OperationType>(type);
}

static V1_2::OperationType convertToV1_2(V1_1::OperationType type) {
    return static_cast<V1_2::OperationType>(type);
}

V1_2::OperationType uncheckedConvertToV1_2(V1_3::OperationType type) {
    return static_cast<V1_2::OperationType>(type);
}

static V1_3::OperationType convertToV1_3(V1_0::OperationType type) {
    return static_cast<V1_3::OperationType>(type);
}

static V1_3::OperationType convertToV1_3(V1_1::OperationType type) {
    return static_cast<V1_3::OperationType>(type);
}

static V1_3::OperationType convertToV1_3(V1_2::OperationType type) {
    return static_cast<V1_3::OperationType>(type);
}

V1_0::Capabilities convertToV1_0(const V1_0::Capabilities& capabilities) {
    return capabilities;
}

V1_0::Capabilities convertToV1_0(const V1_1::Capabilities& capabilities) {
    if (!compliantWithV1_0(capabilities)) {
        LOG(ERROR) << "Upcasting non-compliant capabilities " << toString(capabilities)
                   << " from V1_1::Capabilities to V1_0::Capabilities";
    }
    return {.float32Performance = capabilities.float32Performance,
            .quantized8Performance = capabilities.quantized8Performance};
}

V1_0::Capabilities convertToV1_0(const V1_2::Capabilities& capabilities) {
    if (!compliantWithV1_0(capabilities)) {
        LOG(ERROR) << "Upcasting non-compliant capabilities " << toString(capabilities)
                   << " from V1_2::Capabilities to V1_0::Capabilities";
    }
    return {.float32Performance =
                    lookup(capabilities.operandPerformance, V1_2::OperandType::TENSOR_FLOAT32),
            .quantized8Performance = lookup(capabilities.operandPerformance,
                                            V1_2::OperandType::TENSOR_QUANT8_ASYMM)};
}

V1_0::Capabilities convertToV1_0(const V1_3::Capabilities& capabilities) {
    if (!compliantWithV1_0(capabilities)) {
        LOG(ERROR) << "Upcasting non-compliant capabilities " << toString(capabilities)
                   << " from V1_3::Capabilities to V1_0::Capabilities";
    }
    return {.float32Performance =
                    lookup(capabilities.operandPerformance, V1_3::OperandType::TENSOR_FLOAT32),
            .quantized8Performance = lookup(capabilities.operandPerformance,
                                            V1_3::OperandType::TENSOR_QUANT8_ASYMM)};
}

V1_1::Capabilities convertToV1_1(const V1_0::Capabilities& capabilities) {
    return {.float32Performance = capabilities.float32Performance,
            .quantized8Performance = capabilities.quantized8Performance,
            .relaxedFloat32toFloat16Performance = capabilities.float32Performance};
}

V1_1::Capabilities convertToV1_1(const V1_1::Capabilities& capabilities) {
    return capabilities;
}

V1_1::Capabilities convertToV1_1(const V1_2::Capabilities& capabilities) {
    if (!compliantWithV1_1(capabilities)) {
        LOG(ERROR) << "Upcasting non-compliant capabilities " << toString(capabilities)
                   << " from V1_2::Capabilities to V1_1::Capabilities";
    }
    return {.float32Performance =
                    lookup(capabilities.operandPerformance, V1_2::OperandType::TENSOR_FLOAT32),
            .quantized8Performance =
                    lookup(capabilities.operandPerformance, V1_2::OperandType::TENSOR_QUANT8_ASYMM),
            .relaxedFloat32toFloat16Performance =
                    capabilities.relaxedFloat32toFloat16PerformanceTensor};
}

V1_1::Capabilities convertToV1_1(const V1_3::Capabilities& capabilities) {
    if (!compliantWithV1_1(capabilities)) {
        LOG(ERROR) << "Upcasting non-compliant capabilities " << toString(capabilities)
                   << " from V1_3::Capabilities to V1_1::Capabilities";
    }
    return {.float32Performance =
                    lookup(capabilities.operandPerformance, V1_3::OperandType::TENSOR_FLOAT32),
            .quantized8Performance =
                    lookup(capabilities.operandPerformance, V1_3::OperandType::TENSOR_QUANT8_ASYMM),
            .relaxedFloat32toFloat16Performance =
                    capabilities.relaxedFloat32toFloat16PerformanceTensor};
}

V1_2::Capabilities convertToV1_2(const V1_0::Capabilities& capabilities) {
    V1_2::Capabilities ret = {
            .relaxedFloat32toFloat16PerformanceScalar = capabilities.float32Performance,
            .relaxedFloat32toFloat16PerformanceTensor = capabilities.float32Performance,
            .operandPerformance =
                    makeQuantized8PerformanceConsistentWithP(capabilities.quantized8Performance)};
    auto& opPerf = ret.operandPerformance;
    opPerf.resize(opPerf.size() + 2);
    opPerf[opPerf.size() - 2] = {V1_2::OperandType::TENSOR_FLOAT32,
                                 capabilities.float32Performance};
    opPerf[opPerf.size() - 1] = {V1_2::OperandType::FLOAT32, capabilities.float32Performance};
    using OperandPerformance = V1_2::Capabilities::OperandPerformance;
    std::sort(opPerf.begin(), opPerf.end(),
              [](const OperandPerformance& a, const OperandPerformance& b) {
                  return a.type < b.type;
              });
    return ret;
}

V1_2::Capabilities convertToV1_2(const V1_1::Capabilities& capabilities) {
    V1_2::Capabilities ret = {.relaxedFloat32toFloat16PerformanceScalar =
                                      capabilities.relaxedFloat32toFloat16Performance,
                              .relaxedFloat32toFloat16PerformanceTensor =
                                      capabilities.relaxedFloat32toFloat16Performance,
                              .operandPerformance = makeQuantized8PerformanceConsistentWithP(
                                      capabilities.quantized8Performance)};
    auto& opPerf = ret.operandPerformance;
    opPerf.resize(opPerf.size() + 2);
    opPerf[opPerf.size() - 2] = {V1_2::OperandType::TENSOR_FLOAT32,
                                 capabilities.float32Performance};
    opPerf[opPerf.size() - 1] = {V1_2::OperandType::FLOAT32, capabilities.float32Performance};
    using OperandPerformance = V1_2::Capabilities::OperandPerformance;
    std::sort(opPerf.begin(), opPerf.end(),
              [](const OperandPerformance& a, const OperandPerformance& b) {
                  return a.type < b.type;
              });
    return ret;
}

V1_2::Capabilities convertToV1_2(const V1_2::Capabilities& capabilities) {
    return capabilities;
}

V1_2::Capabilities convertToV1_2(const V1_3::Capabilities& capabilities) {
    V1_2::Capabilities ret = {
            .relaxedFloat32toFloat16PerformanceScalar =
                    capabilities.relaxedFloat32toFloat16PerformanceScalar,
            .relaxedFloat32toFloat16PerformanceTensor =
                    capabilities.relaxedFloat32toFloat16PerformanceTensor,
    };
    const auto& inputOpPerf = capabilities.operandPerformance;
    hardware::hidl_vec<V1_3::Capabilities::OperandPerformance> opPerfSupported;
    opPerfSupported.resize(inputOpPerf.size());
    auto last =
            std::copy_if(inputOpPerf.begin(), inputOpPerf.end(), opPerfSupported.begin(),
                         [](V1_3::Capabilities::OperandPerformance opPerf) {
                             return validOperandType(static_cast<V1_2::OperandType>(opPerf.type));
                         });
    opPerfSupported.resize(std::distance(opPerfSupported.begin(), last));

    auto& convertedOpPerf = ret.operandPerformance;
    convertedOpPerf.resize(opPerfSupported.size());
    std::transform(opPerfSupported.begin(), opPerfSupported.end(), convertedOpPerf.begin(),
                   [](V1_3::Capabilities::OperandPerformance opPerf) {
                       return V1_2::Capabilities::OperandPerformance{
                               static_cast<V1_2::OperandType>(opPerf.type), opPerf.info};
                   });
    return ret;
}

V1_3::Capabilities convertToV1_3(const V1_0::Capabilities& capabilities) {
    return convertToV1_3(convertToV1_2(capabilities));
}

V1_3::Capabilities convertToV1_3(const V1_1::Capabilities& capabilities) {
    return convertToV1_3(convertToV1_2(capabilities));
}

V1_3::Capabilities convertToV1_3(const V1_2::Capabilities& capabilities) {
    V1_3::Capabilities ret = {
            .relaxedFloat32toFloat16PerformanceScalar =
                    capabilities.relaxedFloat32toFloat16PerformanceScalar,
            .relaxedFloat32toFloat16PerformanceTensor =
                    capabilities.relaxedFloat32toFloat16PerformanceTensor,
            .ifPerformance = kNoPerformanceInfo,
            .whilePerformance = kNoPerformanceInfo,
    };
    auto& opPerf = ret.operandPerformance;
    opPerf.resize(capabilities.operandPerformance.size());
    std::transform(capabilities.operandPerformance.begin(), capabilities.operandPerformance.end(),
                   opPerf.begin(), [](V1_2::Capabilities::OperandPerformance opPerf) {
                       return V1_3::Capabilities::OperandPerformance{
                               static_cast<V1_3::OperandType>(opPerf.type), opPerf.info};
                   });
    return ret;
}

V1_3::Capabilities convertToV1_3(const V1_3::Capabilities& capabilities) {
    return capabilities;
}

static V1_0::Operation uncheckedConvertToV1_0(const V1_1::Operation& operation) {
    return {.type = uncheckedConvertToV1_0(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static V1_1::Operation convertToV1_1(const V1_0::Operation& operation) {
    return {.type = convertToV1_1(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static hardware::hidl_vec<V1_0::Operation> uncheckedConvertToV1_0(
        const hardware::hidl_vec<V1_1::Operation>& operations) {
    hardware::hidl_vec<V1_0::Operation> result(operations.size());
    std::transform(
            operations.begin(), operations.end(), result.begin(),
            [](const V1_1::Operation& operation) { return uncheckedConvertToV1_0(operation); });
    return result;
}

static hardware::hidl_vec<V1_1::Operation> convertToV1_1(
        const hardware::hidl_vec<V1_0::Operation>& operations) {
    hardware::hidl_vec<V1_1::Operation> result(operations.size());
    std::transform(operations.begin(), operations.end(), result.begin(),
                   [](const V1_0::Operation& operation) { return convertToV1_1(operation); });
    return result;
}

bool compliantWithV1_0(const V1_3::Operand& operand) {
    return validOperandType(static_cast<V1_0::OperandType>(operand.type)) &&
           (nonExtensionOperandTypeIsScalar(static_cast<int>(operand.type)) ||
            operand.dimensions.size() != 0) &&
           compliantWithV1_0(operand.lifetime);
}

bool compliantWithV1_2(const V1_3::Operand& operand) {
    return validOperandType(static_cast<V1_2::OperandType>(operand.type)) &&
           compliantWithV1_0(operand.lifetime);
}

bool compliantWithV1_3(const V1_3::Operand& /*operand*/) {
    return true;
}

bool compliantWithAidl(const V1_3::Operand& operand) {
    if (static_cast<std::underlying_type_t<V1_3::OperandType>>(operand.type) >
        std::numeric_limits<int32_t>::max()) {
        return false;
    }
    if (operand.location.poolIndex > std::numeric_limits<int32_t>::max()) {
        return false;
    }
    if (operand.extraParams.getDiscriminator() ==
                V1_2::Operand::ExtraParams::hidl_discriminator::channelQuant &&
        operand.extraParams.channelQuant().channelDim > std::numeric_limits<int32_t>::max()) {
        return false;
    }
    for (auto dim : operand.dimensions) {
        if (dim > std::numeric_limits<int32_t>::max()) {
            return false;
        }
    }
    return true;
}

static bool compliantWith(HalVersion version, const V1_3::Model& model,
                          std::set<uint32_t>* noncompliantOperations) {
    // A boolean vector indicating whether each pool is compliant with the target HAL version.
    std::vector<bool> isPoolCompliant(model.pools.size(), false);
    std::transform(
            model.pools.begin(), model.pools.end(), isPoolCompliant.begin(),
            [version](const hardware::hidl_memory& pool) { return validatePool(pool, version); });

    // A boolean vector indicating whether each operand is compliant with the target HAL version.
    std::vector<bool> isOperandCompliant(model.main.operands.size(), false);
    std::transform(model.main.operands.begin(), model.main.operands.end(),
                   isOperandCompliant.begin(),
                   [&isPoolCompliant, version](const V1_3::Operand& op) {
                       bool is_operand_compliant = false;
                       switch (version) {
                           case HalVersion::UNKNOWN:
                               is_operand_compliant = false;
                               break;
                           case HalVersion::V1_0:
                               is_operand_compliant = compliantWithV1_0(op);
                               break;
                           case HalVersion::V1_1:
                               // There is no V1_1::Operand -- both V1_0::Model
                               // and V1_1::Model use V1_0::Operand.
                               is_operand_compliant = compliantWithV1_0(op);
                               break;
                           case HalVersion::V1_2:
                               is_operand_compliant = compliantWithV1_2(op);
                               break;
                           case HalVersion::V1_3:
                               is_operand_compliant = compliantWithV1_3(op);
                               break;
                           case HalVersion::AIDL_V1:
                           case HalVersion::AIDL_V2:
                           case HalVersion::AIDL_UNSTABLE:
                               is_operand_compliant = compliantWithAidl(op);
                               break;
                       }
                       return is_operand_compliant &&
                              !(op.lifetime == V1_3::OperandLifeTime::CONSTANT_REFERENCE &&
                                !isPoolCompliant[op.location.poolIndex]);
                   });

    auto allOperandsCompliant = [&isOperandCompliant](const hardware::hidl_vec<uint32_t>& indices) {
        return std::all_of(
                indices.begin(), indices.end(),
                [&isOperandCompliant](const uint32_t ind) { return isOperandCompliant[ind]; });
    };

    auto localValidateOperation = [&model, version,
                                   &allOperandsCompliant](const V1_3::Operation& op) {
        if (!allOperandsCompliant(op.inputs) || !allOperandsCompliant(op.outputs)) return false;
        int error = validateOperation(static_cast<int32_t>(op.type), op.inputs.size(),
                                      op.inputs.size() > 0 ? op.inputs.data() : nullptr,
                                      op.outputs.size(),
                                      op.outputs.size() > 0 ? op.outputs.data() : nullptr,
                                      uncheckedConvert(model.main.operands), version);
        return error == ANEURALNETWORKS_NO_ERROR;
    };

    if (noncompliantOperations) {
        CHECK(noncompliantOperations->empty());
        for (uint32_t idx = 0; idx < model.main.operations.size(); ++idx) {
            if (!localValidateOperation(model.main.operations[idx])) {
                noncompliantOperations->insert(idx);
            }
        }
        return noncompliantOperations->empty();
    } else {
        return std::all_of(model.main.operations.begin(), model.main.operations.end(),
                           localValidateOperation);
    }
}

bool compliantWithV1_0(const V1_0::Model& /*model*/) {
    return true;
}

bool compliantWithV1_0(const V1_1::Model& model) {
    // In addition to new enumeration values being introduced in V1_1::Model, a
    // new flag was introduced to indicate whether or not float32 data can be
    // calculated using float16 units. This 'relaxComputationFloat32toFloat16'
    // flag is not relevant in whether a V1_1::Model is compliant with a
    // V1_0::Model because all 1.0 drivers require strict calculation by default
    // in the P NN runtime. Even if fp16 calculations are allowed, they can
    // still be computed by a strict fp32 driver.
    auto operands = uncheckedConvert(convertToV1_3(model.operands));
    return std::all_of(model.operations.begin(), model.operations.end(),
                       [&operands](const V1_1::Operation& op) {
                           int error = validateOperation(
                                   static_cast<int32_t>(op.type), op.inputs.size(),
                                   op.inputs.size() > 0 ? op.inputs.data() : nullptr,
                                   op.outputs.size(),
                                   op.outputs.size() > 0 ? op.outputs.data() : nullptr, operands,
                                   HalVersion::V1_0);
                           return error == ANEURALNETWORKS_NO_ERROR;
                       });
}

bool compliantWithV1_0(const V1_2::Model& model, std::set<uint32_t>* noncompliantOperations) {
    return compliantWith(HalVersion::V1_0, convertToV1_3(model), noncompliantOperations);
}

bool compliantWithV1_0(const V1_3::Model& model, std::set<uint32_t>* noncompliantOperations) {
    return compliantWith(HalVersion::V1_0, model, noncompliantOperations);
}

bool compliantWithV1_1(const V1_0::Model&) {
    return true;
}

bool compliantWithV1_1(const V1_1::Model&) {
    return true;
}

bool compliantWithV1_1(const V1_2::Model& model, std::set<uint32_t>* noncompliantOperations) {
    return compliantWith(HalVersion::V1_1, convertToV1_3(model), noncompliantOperations);
}

bool compliantWithV1_1(const V1_3::Model& model, std::set<uint32_t>* noncompliantOperations) {
    return compliantWith(HalVersion::V1_1, model, noncompliantOperations);
}

bool compliantWithV1_2(const V1_0::Model&) {
    return true;
}

bool compliantWithV1_2(const V1_1::Model&) {
    return true;
}

bool compliantWithV1_2(const V1_2::Model&, std::set<uint32_t>* /*noncompliantOperations*/) {
    return true;
}

bool compliantWithV1_2(const V1_3::Model& model, std::set<uint32_t>* noncompliantOperations) {
    return compliantWith(HalVersion::V1_2, model, noncompliantOperations);
}

static V1_0::Operation uncheckedConvertToV1_0(const V1_2::Operation& operation) {
    return {.type = uncheckedConvertToV1_0(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static V1_0::Operation uncheckedConvertToV1_0(const V1_3::Operation& operation) {
    return {.type = uncheckedConvertToV1_0(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static V1_1::Operation uncheckedConvertToV1_1(const V1_2::Operation& operation) {
    return {.type = uncheckedConvertToV1_1(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static V1_1::Operation uncheckedConvertToV1_1(const V1_3::Operation& operation) {
    return {.type = uncheckedConvertToV1_1(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static V1_2::Operation convertToV1_2(const V1_0::Operation& operation) {
    return {.type = convertToV1_2(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static V1_2::Operation convertToV1_2(const V1_1::Operation& operation) {
    return {.type = convertToV1_2(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static V1_2::Operation uncheckedConvertToV1_2(const V1_3::Operation& operation) {
    return {.type = uncheckedConvertToV1_2(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static V1_3::Operation convertToV1_3(const V1_0::Operation& operation) {
    return {.type = convertToV1_3(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static V1_3::Operation convertToV1_3(const V1_1::Operation& operation) {
    return {.type = convertToV1_3(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static V1_3::Operation convertToV1_3(const V1_2::Operation& operation) {
    return {.type = convertToV1_3(operation.type),
            .inputs = operation.inputs,
            .outputs = operation.outputs};
}

static hardware::hidl_vec<V1_0::Operation> uncheckedConvertToV1_0(
        const hardware::hidl_vec<V1_3::Operation>& operations) {
    hardware::hidl_vec<V1_0::Operation> result(operations.size());
    std::transform(
            operations.begin(), operations.end(), result.begin(),
            [](const V1_3::Operation& operation) { return uncheckedConvertToV1_0(operation); });
    return result;
}

static hardware::hidl_vec<V1_0::Operation> uncheckedConvertToV1_0(
        const hardware::hidl_vec<V1_2::Operation>& operations) {
    hardware::hidl_vec<V1_0::Operation> result(operations.size());
    std::transform(
            operations.begin(), operations.end(), result.begin(),
            [](const V1_2::Operation& operation) { return uncheckedConvertToV1_0(operation); });
    return result;
}

static hardware::hidl_vec<V1_2::Operation> uncheckedConvertToV1_2(
        const hardware::hidl_vec<V1_3::Operation>& operations) {
    hardware::hidl_vec<V1_2::Operation> result(operations.size());
    std::transform(
            operations.begin(), operations.end(), result.begin(),
            [](const V1_3::Operation& operation) { return uncheckedConvertToV1_2(operation); });
    return result;
}

static hardware::hidl_vec<V1_1::Operation> uncheckedConvertToV1_1(
        const hardware::hidl_vec<V1_2::Operation>& operations) {
    hardware::hidl_vec<V1_1::Operation> result(operations.size());
    std::transform(
            operations.begin(), operations.end(), result.begin(),
            [](const V1_2::Operation& operation) { return uncheckedConvertToV1_1(operation); });
    return result;
}

static hardware::hidl_vec<V1_1::Operation> uncheckedConvertToV1_1(
        const hardware::hidl_vec<V1_3::Operation>& operations) {
    hardware::hidl_vec<V1_1::Operation> result(operations.size());
    std::transform(
            operations.begin(), operations.end(), result.begin(),
            [](const V1_3::Operation& operation) { return uncheckedConvertToV1_1(operation); });
    return result;
}

static hardware::hidl_vec<V1_2::Operation> convertToV1_2(
        const hardware::hidl_vec<V1_0::Operation>& operations) {
    hardware::hidl_vec<V1_2::Operation> result(operations.size());
    std::transform(operations.begin(), operations.end(), result.begin(),
                   [](const V1_0::Operation& operation) { return convertToV1_2(operation); });
    return result;
}

static hardware::hidl_vec<V1_2::Operation> convertToV1_2(
        const hardware::hidl_vec<V1_1::Operation>& operations) {
    hardware::hidl_vec<V1_2::Operation> result(operations.size());
    std::transform(operations.begin(), operations.end(), result.begin(),
                   [](const V1_1::Operation& operation) { return convertToV1_2(operation); });
    return result;
}

static hardware::hidl_vec<V1_3::Operation> convertToV1_3(
        const hardware::hidl_vec<V1_0::Operation>& operations) {
    hardware::hidl_vec<V1_3::Operation> result(operations.size());
    std::transform(operations.begin(), operations.end(), result.begin(),
                   [](const V1_0::Operation& operation) { return convertToV1_3(operation); });
    return result;
}

static hardware::hidl_vec<V1_3::Operation> convertToV1_3(
        const hardware::hidl_vec<V1_1::Operation>& operations) {
    hardware::hidl_vec<V1_3::Operation> result(operations.size());
    std::transform(operations.begin(), operations.end(), result.begin(),
                   [](const V1_1::Operation& operation) { return convertToV1_3(operation); });
    return result;
}

static hardware::hidl_vec<V1_3::Operation> convertToV1_3(
        const hardware::hidl_vec<V1_2::Operation>& operations) {
    hardware::hidl_vec<V1_3::Operation> result(operations.size());
    std::transform(operations.begin(), operations.end(), result.begin(),
                   [](const V1_2::Operation& operation) { return convertToV1_3(operation); });
    return result;
}

static bool compliantWithV1_0(const V1_2::OperandType& operandType) {
    return validOperandType(static_cast<V1_0::OperandType>(operandType));
}

static bool compliantWithV1_0(const V1_3::OperandType& operandType) {
    return validOperandType(static_cast<V1_0::OperandType>(operandType));
}

static bool compliantWithV1_2(const V1_3::OperandType& operandType) {
    return validOperandType(static_cast<V1_2::OperandType>(operandType));
}

V1_0::OperandType convertToV1_0(const V1_2::OperandType& operandType) {
    if (!compliantWithV1_0(operandType)) {
        LOG(ERROR) << "Upcasting non-compliant operand type " << toString(operandType)
                   << " from V1_2::OperandType to V1_0::OperandType";
    }
    return static_cast<V1_0::OperandType>(operandType);
}

V1_2::OperandType convertToV1_2(const V1_0::OperandType& operandType) {
    return static_cast<V1_2::OperandType>(operandType);
}

V1_2::OperandType convertToV1_2(const V1_3::OperandType& operandType) {
    if (!compliantWithV1_2(operandType)) {
        LOG(ERROR) << "Upcasting non-compliant operand type " << toString(operandType)
                   << " from V1_3::OperandType to V1_2::OperandType";
    }
    return static_cast<V1_2::OperandType>(operandType);
}

V1_0::OperandType convertToV1_0(const V1_3::OperandType& operandType) {
    if (!compliantWithV1_0(operandType)) {
        LOG(ERROR) << "Upcasting non-compliant operand type " << toString(operandType)
                   << " from V1_3::Operand to V1_0::Operand";
    }
    return static_cast<V1_0::OperandType>(operandType);
}

bool compliantWithV1_0(V1_0::OperandLifeTime /*lifetime*/) {
    return true;
}

bool compliantWithV1_0(V1_3::OperandLifeTime lifetime) {
    return lifetime != V1_3::OperandLifeTime::SUBGRAPH;
}

bool compliantWithV1_3(V1_0::OperandLifeTime /*lifetime*/) {
    return true;
}

bool compliantWithV1_3(V1_3::OperandLifeTime /*lifetime*/) {
    return true;
}

V1_0::OperandLifeTime convertToV1_0(V1_0::OperandLifeTime lifetime) {
    return lifetime;
}

V1_0::OperandLifeTime convertToV1_0(V1_3::OperandLifeTime lifetime) {
    if (!compliantWithV1_0(lifetime)) {
        LOG(ERROR) << "Upcasting non-compliant lifetime " << toString(lifetime)
                   << " from V1_3 to V1_0";
    }
    return static_cast<V1_0::OperandLifeTime>(lifetime);
}

V1_3::OperandLifeTime convertToV1_3(V1_0::OperandLifeTime lifetime) {
    return static_cast<V1_3::OperandLifeTime>(lifetime);
}

V1_3::OperandLifeTime convertToV1_3(V1_3::OperandLifeTime lifetime) {
    return lifetime;
}

V1_0::Operand convertToV1_0(const V1_2::Operand& operand) {
    return {.type = convertToV1_0(operand.type),
            .dimensions = operand.dimensions,
            .numberOfConsumers = operand.numberOfConsumers,
            .scale = operand.scale,
            .zeroPoint = operand.zeroPoint,
            .lifetime = convertToV1_0(operand.lifetime),
            .location = operand.location};
}

V1_0::Operand convertToV1_0(const V1_3::Operand& operand) {
    return {.type = convertToV1_0(operand.type),
            .dimensions = operand.dimensions,
            .numberOfConsumers = operand.numberOfConsumers,
            .scale = operand.scale,
            .zeroPoint = operand.zeroPoint,
            .lifetime = convertToV1_0(operand.lifetime),
            .location = operand.location};
}

V1_2::Operand convertToV1_2(const V1_0::Operand& operand) {
    return {.type = convertToV1_2(operand.type),
            .dimensions = operand.dimensions,
            .numberOfConsumers = operand.numberOfConsumers,
            .scale = operand.scale,
            .zeroPoint = operand.zeroPoint,
            .lifetime = operand.lifetime,
            .location = operand.location};
}

V1_2::Operand convertToV1_2(const V1_3::Operand& operand) {
    return {.type = convertToV1_2(operand.type),
            .dimensions = operand.dimensions,
            .numberOfConsumers = operand.numberOfConsumers,
            .scale = operand.scale,
            .zeroPoint = operand.zeroPoint,
            .lifetime = static_cast<V1_0::OperandLifeTime>(operand.lifetime),
            .location = operand.location,
            .extraParams = operand.extraParams};
}

V1_3::Operand convertToV1_3(const V1_0::Operand& operand) {
    return {.type = static_cast<V1_3::OperandType>(operand.type),
            .dimensions = operand.dimensions,
            .numberOfConsumers = operand.numberOfConsumers,
            .scale = operand.scale,
            .zeroPoint = operand.zeroPoint,
            .lifetime = convertToV1_3(operand.lifetime),
            .location = operand.location};
}

V1_3::Operand convertToV1_3(const V1_2::Operand& operand) {
    return {.type = static_cast<V1_3::OperandType>(operand.type),
            .dimensions = operand.dimensions,
            .numberOfConsumers = operand.numberOfConsumers,
            .scale = operand.scale,
            .zeroPoint = operand.zeroPoint,
            .lifetime = convertToV1_3(operand.lifetime),
            .location = operand.location,
            .extraParams = operand.extraParams};
}

V1_3::Operand convertToV1_3(const V1_3::Operand& operand) {
    return operand;
}

hardware::hidl_vec<V1_0::Operand> convertToV1_0(const hardware::hidl_vec<V1_0::Operand>& operands) {
    return operands;
}

hardware::hidl_vec<V1_0::Operand> convertToV1_0(const hardware::hidl_vec<V1_2::Operand>& operands) {
    hardware::hidl_vec<V1_0::Operand> result(operands.size());
    std::transform(operands.begin(), operands.end(), result.begin(),
                   [](const V1_2::Operand& operand) { return convertToV1_0(operand); });
    return result;
}

hardware::hidl_vec<V1_0::Operand> convertToV1_0(const hardware::hidl_vec<V1_3::Operand>& operands) {
    hardware::hidl_vec<V1_0::Operand> result(operands.size());
    std::transform(operands.begin(), operands.end(), result.begin(),
                   [](const V1_3::Operand& operand) { return convertToV1_0(operand); });
    return result;
}

hardware::hidl_vec<V1_2::Operand> convertToV1_2(const hardware::hidl_vec<V1_0::Operand>& operands) {
    hardware::hidl_vec<V1_2::Operand> result(operands.size());
    std::transform(operands.begin(), operands.end(), result.begin(),
                   [](const V1_0::Operand& operand) { return convertToV1_2(operand); });
    return result;
}

hardware::hidl_vec<V1_2::Operand> convertToV1_2(const hardware::hidl_vec<V1_2::Operand>& operands) {
    return operands;
}

hardware::hidl_vec<V1_2::Operand> convertToV1_2(const hardware::hidl_vec<V1_3::Operand>& operands) {
    hardware::hidl_vec<V1_2::Operand> result(operands.size());
    std::transform(operands.begin(), operands.end(), result.begin(),
                   [](const V1_3::Operand& operand) { return convertToV1_2(operand); });
    return result;
}

hardware::hidl_vec<V1_3::Operand> convertToV1_3(const hardware::hidl_vec<V1_0::Operand>& operands) {
    hardware::hidl_vec<V1_3::Operand> result(operands.size());
    std::transform(operands.begin(), operands.end(), result.begin(),
                   [](const V1_0::Operand& operand) { return convertToV1_3(operand); });
    return result;
}

hardware::hidl_vec<V1_3::Operand> convertToV1_3(const hardware::hidl_vec<V1_2::Operand>& operands) {
    hardware::hidl_vec<V1_3::Operand> result(operands.size());
    std::transform(operands.begin(), operands.end(), result.begin(),
                   [](const V1_2::Operand& operand) { return convertToV1_3(operand); });
    return result;
}

hardware::hidl_vec<V1_3::Operand> convertToV1_3(const hardware::hidl_vec<V1_3::Operand>& operands) {
    return operands;
}

V1_0::Model convertToV1_0(const V1_0::Model& model) {
    return model;
}

V1_0::Model convertToV1_0(const V1_1::Model& model) {
    if (!compliantWithV1_0(model)) {
        LOG(ERROR) << "Upcasting non-compliant model " << SHOW_IF_DEBUG(toString(model))
                   << " from V1_1::Model to V1_0::Model";
    }
    return {.operands = model.operands,
            .operations = uncheckedConvertToV1_0(model.operations),
            .inputIndexes = model.inputIndexes,
            .outputIndexes = model.outputIndexes,
            .operandValues = model.operandValues,
            .pools = model.pools};
}

V1_0::Model convertToV1_0(const V1_2::Model& model) {
    if (!compliantWithV1_0(model)) {
        LOG(ERROR) << "Upcasting non-compliant model " << SHOW_IF_DEBUG(toString(model))
                   << " from V1_2::Model to V1_0::Model";
    }
    return {.operands = convertToV1_0(model.operands),
            .operations = uncheckedConvertToV1_0(model.operations),
            .inputIndexes = model.inputIndexes,
            .outputIndexes = model.outputIndexes,
            .operandValues = model.operandValues,
            .pools = model.pools};
}

V1_0::Model convertToV1_0(const V1_3::Model& model) {
    if (!compliantWithV1_0(model)) {
        LOG(ERROR) << "Upcasting non-compliant model " << SHOW_IF_DEBUG(toString(model))
                   << " from V1_3::Model to V1_0::Model";
    }
    return {.operands = convertToV1_0(model.main.operands),
            .operations = uncheckedConvertToV1_0(model.main.operations),
            .inputIndexes = model.main.inputIndexes,
            .outputIndexes = model.main.outputIndexes,
            .operandValues = model.operandValues,
            .pools = model.pools};
}

V1_1::Model convertToV1_1(const V1_0::Model& model) {
    return {.operands = model.operands,
            .operations = convertToV1_1(model.operations),
            .inputIndexes = model.inputIndexes,
            .outputIndexes = model.outputIndexes,
            .operandValues = model.operandValues,
            .pools = model.pools,
            .relaxComputationFloat32toFloat16 = false};
}

V1_1::Model convertToV1_1(const V1_1::Model& model) {
    return model;
}

V1_1::Model convertToV1_1(const V1_2::Model& model) {
    if (!compliantWithV1_1(model)) {
        LOG(ERROR) << "Upcasting non-compliant model " << SHOW_IF_DEBUG(toString(model))
                   << " from V1_2::Model to V1_1::Model";
    }
    return {.operands = convertToV1_0(model.operands),  // Operands in 1.1 and 1.0 are identical.
            .operations = uncheckedConvertToV1_1(model.operations),
            .inputIndexes = model.inputIndexes,
            .outputIndexes = model.outputIndexes,
            .operandValues = model.operandValues,
            .pools = model.pools,
            .relaxComputationFloat32toFloat16 = model.relaxComputationFloat32toFloat16};
}

V1_1::Model convertToV1_1(const V1_3::Model& model) {
    if (!compliantWithV1_1(model)) {
        LOG(ERROR) << "Upcasting non-compliant model " << SHOW_IF_DEBUG(toString(model))
                   << " from V1_3::Model to V1_1::Model";
    }
    return {// Operands in 1.1 and 1.0 are identical.
            .operands = convertToV1_0(model.main.operands),
            .operations = uncheckedConvertToV1_1(model.main.operations),
            .inputIndexes = model.main.inputIndexes,
            .outputIndexes = model.main.outputIndexes,
            .operandValues = model.operandValues,
            .pools = model.pools,
            .relaxComputationFloat32toFloat16 = model.relaxComputationFloat32toFloat16};
}

V1_2::Model convertToV1_2(const V1_0::Model& model) {
    return {.operands = convertToV1_2(model.operands),
            .operations = convertToV1_2(model.operations),
            .inputIndexes = model.inputIndexes,
            .outputIndexes = model.outputIndexes,
            .operandValues = model.operandValues,
            .pools = model.pools,
            .relaxComputationFloat32toFloat16 = false};
}

V1_2::Model convertToV1_2(const V1_1::Model& model) {
    return {.operands = convertToV1_2(model.operands),
            .operations = convertToV1_2(model.operations),
            .inputIndexes = model.inputIndexes,
            .outputIndexes = model.outputIndexes,
            .operandValues = model.operandValues,
            .pools = model.pools,
            .relaxComputationFloat32toFloat16 = model.relaxComputationFloat32toFloat16};
}

V1_2::Model convertToV1_2(const V1_2::Model& model) {
    return model;
}

V1_2::Model convertToV1_2(const V1_3::Model& model) {
    if (!compliantWithV1_2(model)) {
        LOG(ERROR) << "Upcasting non-compliant model " << SHOW_IF_DEBUG(toString(model))
                   << " from V1_3::Model to V1_2::Model";
    }
    return {.operands = convertToV1_2(model.main.operands),
            .operations = uncheckedConvertToV1_2(model.main.operations),
            .inputIndexes = model.main.inputIndexes,
            .outputIndexes = model.main.outputIndexes,
            .operandValues = model.operandValues,
            .pools = model.pools,
            .relaxComputationFloat32toFloat16 = model.relaxComputationFloat32toFloat16,
            .extensionNameToPrefix = model.extensionNameToPrefix};
}

V1_3::Model convertToV1_3(const V1_0::Model& model) {
    return {.main = {.operands = convertToV1_3(model.operands),
                     .operations = convertToV1_3(model.operations),
                     .inputIndexes = model.inputIndexes,
                     .outputIndexes = model.outputIndexes},
            .operandValues = model.operandValues,
            .pools = model.pools,
            .relaxComputationFloat32toFloat16 = false};
}

V1_3::Model convertToV1_3(const V1_1::Model& model) {
    return {.main = {.operands = convertToV1_3(model.operands),
                     .operations = convertToV1_3(model.operations),
                     .inputIndexes = model.inputIndexes,
                     .outputIndexes = model.outputIndexes},
            .operandValues = model.operandValues,
            .pools = model.pools,
            .relaxComputationFloat32toFloat16 = model.relaxComputationFloat32toFloat16};
}

V1_3::Model convertToV1_3(const V1_2::Model& model) {
    return {.main = {.operands = convertToV1_3(model.operands),
                     .operations = convertToV1_3(model.operations),
                     .inputIndexes = model.inputIndexes,
                     .outputIndexes = model.outputIndexes},
            .operandValues = model.operandValues,
            .pools = model.pools,
            .relaxComputationFloat32toFloat16 = model.relaxComputationFloat32toFloat16,
            .extensionNameToPrefix = model.extensionNameToPrefix};
}

V1_3::Model convertToV1_3(const V1_3::Model& model) {
    return model;
}

bool compliantWithV1_0(const V1_0::Request& /*request*/) {
    return true;
}

bool compliantWithV1_0(const V1_3::Request& request) {
    return std::all_of(request.pools.begin(), request.pools.end(), [](const auto& pool) {
        if (pool.getDiscriminator() != V1_3::Request::MemoryPool::hidl_discriminator::hidlMemory) {
            return false;
        }
        const auto& name = pool.hidlMemory().name();
        return name == "ashmem" || name == "mmap_fd";
    });
}

bool compliantWithV1_2(const V1_3::Request& request) {
    return std::all_of(request.pools.begin(), request.pools.end(), [](const auto& pool) {
        if (pool.getDiscriminator() != V1_3::Request::MemoryPool::hidl_discriminator::hidlMemory) {
            return false;
        }
        const auto& name = pool.hidlMemory().name();
        return name == "ashmem" || name == "mmap_fd" || name == "hardware_buffer_blob" ||
               name == "hardware_buffer";
    });
}

static hardware::hidl_memory convertToV1_0(const V1_3::Request::MemoryPool& pool) {
    switch (pool.getDiscriminator()) {
        case V1_3::Request::MemoryPool::hidl_discriminator::hidlMemory:
            return pool.hidlMemory();
        case V1_3::Request::MemoryPool::hidl_discriminator::token:
            return hardware::hidl_memory{};
    }
}

static V1_3::Request::MemoryPool convertToV1_3(const hardware::hidl_memory& pool) {
    V1_3::Request::MemoryPool ret;
    ret.hidlMemory(pool);
    return ret;
}

V1_0::Request convertToV1_0(const V1_0::Request& request) {
    return request;
}

static V1_0::Request uncheckedConvertToV1_0(const V1_3::Request& request) {
    hardware::hidl_vec<hardware::hidl_memory> pools(request.pools.size());
    std::transform(request.pools.begin(), request.pools.end(), pools.begin(),
                   [](const auto& pool) { return convertToV1_0(pool); });
    return {.inputs = request.inputs, .outputs = request.outputs, .pools = std::move(pools)};
}

V1_0::Request convertToV1_0(const V1_3::Request& request) {
    if (!compliantWithV1_0(request)) {
        LOG(ERROR) << "Upcasting non-compliant request " << SHOW_IF_DEBUG(toString(request))
                   << " from V1_3::Request to V1_0::Request of version 1.0";
    }
    return uncheckedConvertToV1_0(request);
}

V1_0::Request convertToV1_2(const V1_3::Request& request) {
    if (!compliantWithV1_2(request)) {
        LOG(ERROR) << "Upcasting non-compliant request " << SHOW_IF_DEBUG(toString(request))
                   << " from V1_3::Request to V1_0::Request of version 1.2";
    }
    return uncheckedConvertToV1_0(request);
}

V1_3::Request convertToV1_3(const V1_0::Request& request) {
    hardware::hidl_vec<V1_3::Request::MemoryPool> pools(request.pools.size());
    std::transform(request.pools.begin(), request.pools.end(), pools.begin(),
                   [](const auto& pool) { return convertToV1_3(pool); });
    return {.inputs = request.inputs, .outputs = request.outputs, .pools = std::move(pools)};
}

V1_3::Request convertToV1_3(const V1_3::Request& request) {
    return request;
}

ErrorStatus uncheckedConvert(V1_0::ErrorStatus status) {
    return handleError(convert(status));
}

ErrorStatus uncheckedConvert(V1_3::ErrorStatus status) {
    return handleError(convert(status));
}

OperandType uncheckedConvert(V1_3::OperandType operandType) {
    return handleError(unvalidatedConvert(operandType));
}

OperationType uncheckedConvert(V1_3::OperationType operandType) {
    return handleError(unvalidatedConvert(operandType));
}

Operand::LifeTime uncheckedConvert(V1_3::OperandLifeTime lifetime) {
    return handleError(unvalidatedConvert(lifetime));
}

MeasureTiming uncheckedConvert(V1_2::MeasureTiming measure) {
    return handleError(convert(measure));
}

DataLocation uncheckedConvert(const V1_0::DataLocation& location) {
    return handleError(unvalidatedConvert(location));
}

Operand uncheckedConvert(const V1_3::Operand& operand) {
    return handleError(unvalidatedConvert(operand));
}

Operand::ExtraParams uncheckedConvert(const V1_2::Operand::ExtraParams& params) {
    return handleError(unvalidatedConvert(params));
}

Operand::SymmPerChannelQuantParams uncheckedConvert(const V1_2::SymmPerChannelQuantParams& params) {
    return handleError(unvalidatedConvert(params));
}

Operand::ExtensionParams uncheckedConvert(const hardware::hidl_vec<uint8_t>& params) {
    return params;
}

Operation uncheckedConvert(const V1_3::Operation& operation) {
    return handleError(unvalidatedConvert(operation));
}

template <typename CanonicalType, typename HalType>
static std::vector<CanonicalType> convertVec(const hardware::hidl_vec<HalType>& items) {
    std::vector<CanonicalType> result;
    result.reserve(items.size());
    std::transform(items.begin(), items.end(), std::back_inserter(result),
                   [](const HalType& item) { return uncheckedConvert(item); });
    return result;
}

Model uncheckedConvert(const V1_3::Model& model) {
    return handleError(convert(model));
}

Model::Subgraph uncheckedConvert(const V1_3::Subgraph& subgraph) {
    return handleError(unvalidatedConvert(subgraph));
}

ExtensionNameAndPrefix uncheckedConvert(const V1_2::Model::ExtensionNameAndPrefix& x) {
    return handleError(unvalidatedConvert(x));
}

Request uncheckedConvert(const V1_3::Request& request) {
    return handleError(convert(request));
}

Request::Argument uncheckedConvert(const V1_0::RequestArgument& requestArgument) {
    return handleError(unvalidatedConvert(requestArgument));
}

Request::MemoryPool uncheckedConvert(const V1_3::Request::MemoryPool& memoryPool) {
    return handleError(unvalidatedConvert(memoryPool));
}

OutputShape uncheckedConvert(const V1_2::OutputShape& outputShape) {
    return handleError(unvalidatedConvert(outputShape));
}

std::vector<OutputShape> uncheckedConvert(
        const hardware::hidl_vec<V1_2::OutputShape>& outputShapes) {
    return convertVec<OutputShape>(outputShapes);
}

Capabilities uncheckedConvert(const V1_3::Capabilities& capabilities) {
    return handleError(convert(capabilities));
}

Capabilities::OperandPerformance uncheckedConvert(
        const V1_3::Capabilities::OperandPerformance& operandPerformance) {
    return handleError(unvalidatedConvert(operandPerformance));
}

Capabilities::PerformanceInfo uncheckedConvert(const V1_0::PerformanceInfo& performanceInfo) {
    return handleError(unvalidatedConvert(performanceInfo));
}

Extension uncheckedConvert(const V1_2::Extension& extension) {
    return handleError(unvalidatedConvert(extension));
}

std::vector<Extension> uncheckedConvert(const hardware::hidl_vec<V1_2::Extension>& extensions) {
    return convertVec<Extension>(extensions);
}

Extension::OperandTypeInformation uncheckedConvert(
        const V1_2::Extension::OperandTypeInformation& info) {
    return handleError(unvalidatedConvert(info));
}

OptionalDuration uncheckedConvert(const V1_3::OptionalTimeoutDuration& timeoutDuration) {
    return handleError(convert(timeoutDuration));
}

Timing uncheckedConvert(const V1_2::Timing& timing) {
    return handleError(convert(timing));
}

V1_0::ErrorStatus convertToV1_0(ErrorStatus status) {
    return static_cast<V1_0::ErrorStatus>(static_cast<int>(status));
}

V1_3::ErrorStatus convertToV1_3(ErrorStatus status) {
    return handleError(V1_3::utils::convert(status));
}

V1_3::OperandType convertToV1_3(OperandType operandType) {
    return handleError(V1_3::utils::unvalidatedConvert(operandType));
}

V1_3::OperationType convertToV1_3(OperationType operandType) {
    return handleError(V1_3::utils::unvalidatedConvert(operandType));
}

V1_3::OperandLifeTime convertToV1_3(Operand::LifeTime lifetime) {
    return handleError(V1_3::utils::unvalidatedConvert(lifetime));
}

V1_1::ExecutionPreference convertToV1_1(ExecutionPreference preference) {
    return handleError(V1_1::utils::convert(preference));
}

V1_3::Priority convertToV1_3(Priority priority) {
    return handleError(V1_3::utils::convert(priority));
}

V1_2::MeasureTiming convertToV1_2(MeasureTiming measure) {
    return handleError(V1_2::utils::convert(measure));
}

V1_0::DataLocation convertToV1_0(const DataLocation& location) {
    return handleError(V1_0::utils::unvalidatedConvert(location));
}

V1_3::Operand convertToV1_3(const Operand& operand) {
    return handleError(V1_3::utils::unvalidatedConvert(operand));
}

V1_2::Operand::ExtraParams convertToV1_2(const Operand::ExtraParams& params) {
    return handleError(V1_2::utils::unvalidatedConvert(params));
}

V1_2::SymmPerChannelQuantParams convertToV1_2(const Operand::SymmPerChannelQuantParams& params) {
    return handleError(V1_2::utils::unvalidatedConvert(params));
}

hardware::hidl_vec<uint8_t> uncheckedConvert(const Operand::ExtensionParams& params) {
    return params;
}

V1_3::Operation convertToV1_3(const Operation& operation) {
    return handleError(V1_3::utils::unvalidatedConvert(operation));
}

template <typename HalType, typename CanonicalType>
static hardware::hidl_vec<HalType> convertVecToV1_0(const std::vector<CanonicalType>& items) {
    hardware::hidl_vec<HalType> result(items.size());
    std::transform(items.begin(), items.end(), result.begin(),
                   [](const CanonicalType& item) { return convertToV1_0(item); });
    return result;
}

template <typename HalType, typename CanonicalType>
static hardware::hidl_vec<HalType> convertVecToV1_2(const std::vector<CanonicalType>& items) {
    hardware::hidl_vec<HalType> result(items.size());
    std::transform(items.begin(), items.end(), result.begin(),
                   [](const CanonicalType& item) { return convertToV1_2(item); });
    return result;
}

template <typename HalType, typename CanonicalType>
static hardware::hidl_vec<HalType> convertVecToV1_3(const std::vector<CanonicalType>& items) {
    hardware::hidl_vec<HalType> result(items.size());
    std::transform(items.begin(), items.end(), result.begin(),
                   [](const CanonicalType& item) { return convertToV1_3(item); });
    return result;
}

V1_2::OutputShape convertToV1_2(const OutputShape& outputShape) {
    return handleError(V1_2::utils::unvalidatedConvert(outputShape));
}

hardware::hidl_vec<V1_2::OutputShape> convertToV1_2(const std::vector<OutputShape>& outputShapes) {
    return convertVecToV1_2<V1_2::OutputShape>(outputShapes);
}

V1_3::Model convertToV1_3(const Model& model) {
    return handleError(V1_3::utils::convert(model));
}

V1_3::Subgraph convertToV1_3(const Model::Subgraph& subgraph) {
    return handleError(V1_3::utils::unvalidatedConvert(subgraph));
}

V1_2::Model::ExtensionNameAndPrefix convertToV1_2(const ExtensionNameAndPrefix& x) {
    return handleError(V1_2::utils::unvalidatedConvert(x));
}

V1_3::Request convertToV1_3(const Request& request) {
    return handleError(V1_3::utils::convert(request));
}

V1_0::RequestArgument convertToV1_0(const Request::Argument& requestArgument) {
    return handleError(V1_0::utils::unvalidatedConvert(requestArgument));
}

V1_3::Request::MemoryPool convertToV1_3(const Request::MemoryPool& memoryPool) {
    return handleError(V1_3::utils::unvalidatedConvert(memoryPool));
}

std::vector<Request::MemoryPool> uncheckedConvert(
        const hardware::hidl_vec<V1_3::Request::MemoryPool>& memoryPools) {
    return convertVec<Request::MemoryPool>(memoryPools);
}

V1_3::OptionalTimePoint convertToV1_3(const OptionalTimePoint& timePoint) {
    return handleError(V1_3::utils::convert(timePoint));
}

V1_3::OptionalTimeoutDuration convertToV1_3(const OptionalDuration& timeoutDuration) {
    return handleError(V1_3::utils::convert(timeoutDuration));
}

V1_2::Timing convertToV1_2(const Timing& timing) {
    return handleError(V1_2::utils::convert(timing));
}

V1_3::BufferRole convertToV1_3(const BufferRole& bufferRole) {
    return handleError(V1_3::utils::unvalidatedConvert(bufferRole));
}

hardware::hidl_vec<V1_3::BufferRole> convertToV1_3(const std::vector<BufferRole>& bufferRoles) {
    return convertVecToV1_3<V1_3::BufferRole>(bufferRoles);
}

hardware::hidl_vec<uint8_t> convertToV1_0(const Model::OperandValues& operandValues) {
    return handleError(V1_0::utils::unvalidatedConvert(operandValues));
}

hardware::hidl_memory convertToV1_0(const SharedMemory& memory) {
    return handleError(V1_0::utils::unvalidatedConvert(memory));
}

SharedMemory uncheckedConvert(const hardware::hidl_memory& memory) {
    return handleError(convert(memory));
}

hardware::hidl_vec<hardware::hidl_memory> convertToV1_0(const std::vector<SharedMemory>& memories) {
    return convertVecToV1_0<hardware::hidl_memory>(memories);
}

std::vector<SharedMemory> uncheckedConvert(
        const hardware::hidl_vec<hardware::hidl_memory>& memories) {
    return convertVec<SharedMemory>(memories);
}

std::vector<Model::Subgraph> uncheckedConvert(const hardware::hidl_vec<V1_3::Subgraph>& subgraphs) {
    return convertVec<Model::Subgraph>(subgraphs);
}

std::vector<Operand> uncheckedConvert(const hardware::hidl_vec<V1_3::Operand>& operands) {
    return convertVec<Operand>(operands);
}

}  // namespace nn
}  // namespace android
