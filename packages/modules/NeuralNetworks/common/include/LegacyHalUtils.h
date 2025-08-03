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
// This file contains pre-canonical-types utility code and includes HAL
// utilities. LegacyUtils.h is the subset of these utilities that do not touch
// HAL.

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_LEGACY_HAL_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_LEGACY_HAL_UTILS_H

#include <android-base/logging.h>
#include <nnapi/TypeUtils.h>
#include <nnapi/Types.h>

#include <set>
#include <string>
#include <tuple>
#include <utility>
#include <vector>

#include "HalInterfaces.h"
#include "LegacyUtils.h"
#include "NeuralNetworks.h"
#include "ValidateHal.h"

namespace android {
namespace nn {

using LegacyClock = std::chrono::steady_clock;
using LegacyDuration = std::chrono::duration<uint64_t, std::nano>;
using LegacyOptionalDuration = std::optional<LegacyDuration>;
using LegacyTimePoint = std::chrono::time_point<std::chrono::steady_clock, LegacyDuration>;
using LegacyOptionalTimePoint = std::optional<LegacyTimePoint>;

// Make an optional deadline from an V1_3::OptionalTimePoint.
LegacyOptionalTimePoint makeDeadline(const V1_3::OptionalTimePoint& timePoint);

// Make an optional deadline from an V1_3::OptionalDuration.
LegacyOptionalTimePoint makeDeadline(const V1_3::OptionalTimeoutDuration& optionalDuration);

// Returns true if the deadline has passed. Returns false if either the deadline
// has not been exceeded or if the deadline is not present.
bool hasDeadlinePassed(const LegacyOptionalTimePoint& deadline);

// Ensure that every user of FalseyErrorStream is linked to the
// correct instance, using the correct LOG_TAG
namespace {

template <HalVersion version>
struct VersionedType {};

template <>
struct VersionedType<HalVersion::V1_2> {
    using OperandPerformance = V1_2::Capabilities::OperandPerformance;
    using OperandType = V1_2::OperandType;
};

template <>
struct VersionedType<HalVersion::V1_3> {
    using OperandPerformance = V1_3::Capabilities::OperandPerformance;
    using OperandType = V1_3::OperandType;
};

template <HalVersion version>
using VersionedOperandPerformance = typename VersionedType<version>::OperandPerformance;
template <HalVersion version>
using VersionedOperandType = typename VersionedType<version>::OperandType;

}  // namespace

// Return a vector with one entry for each non-extension OperandType except
// SUBGRAPH, set to the specified PerformanceInfo value.  The vector will be
// sorted by OperandType.
//
// Control flow (OperandType::SUBGRAPH) operation performance is specified
// separately using Capabilities::ifPerformance and
// Capabilities::whilePerformance.
template <HalVersion version>
hardware::hidl_vec<VersionedOperandPerformance<version>> nonExtensionOperandPerformance(
        V1_0::PerformanceInfo perf);

// Update the vector entry corresponding to the specified OperandType with the
// specified PerformanceInfo value.  The vector must already have an entry for
// that OperandType, and must be sorted by OperandType.
void update(hardware::hidl_vec<V1_2::Capabilities::OperandPerformance>* operandPerformance,
            V1_2::OperandType type, V1_0::PerformanceInfo perf);
void update(hardware::hidl_vec<V1_3::Capabilities::OperandPerformance>* operandPerformance,
            V1_3::OperandType type, V1_0::PerformanceInfo perf);

// Look for a vector entry corresponding to the specified OperandType.  If
// found, return the associated PerformanceInfo.  If not, return a pessimistic
// PerformanceInfo (FLT_MAX).  The vector must be sorted by OperandType.
V1_0::PerformanceInfo lookup(
        const hardware::hidl_vec<V1_2::Capabilities::OperandPerformance>& operandPerformance,
        V1_2::OperandType type);
V1_0::PerformanceInfo lookup(
        const hardware::hidl_vec<V1_3::Capabilities::OperandPerformance>& operandPerformance,
        V1_3::OperandType type);

// Returns true if an operand type is an extension type.
bool isExtensionOperandType(V1_3::OperandType type);

// Returns true if an operation type is an extension type.
bool isExtensionOperationType(V1_3::OperationType type);

// Returns the amount of space needed to store a value of the specified
// dimensions and type. For a tensor with unspecified rank or at least one
// unspecified dimension, returns zero.
//
// Aborts if the specified type is an extension type.
// Aborts if the size would overflow the return type.
//
// See also TypeManager::getSizeOfData(OperandType, const std::vector<uint32_t>&).
uint32_t nonExtensionOperandSizeOfData(V1_3::OperandType type,
                                       const std::vector<uint32_t>& dimensions);

// Returns the amount of space needed to store a value of the dimensions and
// type of this operand. For a tensor with unspecified rank or at least one
// unspecified dimension, returns zero.
//
// Aborts if the specified type is an extension type.
// Aborts if the size would overflow the return type.
//
// See also TypeManager::getSizeOfData(const Operand&).
inline uint32_t nonExtensionOperandSizeOfData(const V1_3::Operand& operand) {
    return nonExtensionOperandSizeOfData(operand.type, operand.dimensions);
}

// Returns true if the amount of space needed to store a value of the specified
// dimensions and element size overflows the uint32_t type.
//
// Aborts if the specified type is an extension type.
//
// See also TypeManager::sizeOfDataOverflowsUInt32(OperandType, const std::vector<uint32_t>&).
bool nonExtensionOperandSizeOfDataOverflowsUInt32(V1_3::OperandType type,
                                                  const std::vector<uint32_t>& dimensions);

// Returns the name of the operation type in ASCII.
std::string getOperationName(V1_3::OperationType opCode);

// Returns the name of the operand type in ASCII.
std::string getOperandTypeName(V1_3::OperandType type);

// Whether an operand of tensor type has unspecified dimensions.
//
// Undefined behavior if the operand type is a scalar type.
bool tensorHasUnspecifiedDimensions(V1_3::OperandType type,
                                    const std::vector<uint32_t>& dimensions);
bool tensorHasUnspecifiedDimensions(const V1_3::Operand& operand);

// Does a detailed LOG(INFO) of the model
void logModelToInfo(const V1_0::Model& model);
void logModelToInfo(const V1_1::Model& model);
void logModelToInfo(const V1_2::Model& model);
void logModelToInfo(const V1_3::Model& model);

bool validateOperandSymmPerChannelQuantParams(
        const V1_3::Operand& halOperand,
        const ANeuralNetworksSymmPerChannelQuantParams& channelQuant, const char* tag);

// Convert ANEURALNETWORKS_* result code to ErrorStatus.
// Not guaranteed to be a 1-to-1 mapping.
V1_3::ErrorStatus convertResultCodeToHalErrorStatus(int resultCode);

// Convert ErrorStatus to ANEURALNETWORKS_* result code.
// Not guaranteed to be a 1-to-1 mapping.
int convertErrorStatusToResultCode(V1_3::ErrorStatus status);

// Convert execution results to runtime format. Additionally checks that the
// returned results abide by the HAL specification, and logs an error if the
// result violates the specification.
std::tuple<int, std::vector<OutputShape>, Timing> getExecutionResult(
        V1_3::ErrorStatus status, const hardware::hidl_vec<V1_2::OutputShape>& outputShapes,
        const V1_2::Timing& timing);

// Forward declaration for type defined in CpuExecutor.h.
class RunTimePoolInfo;

bool setRunTimePoolInfosFromHidlMemories(std::vector<RunTimePoolInfo>* poolInfos,
                                         const hardware::hidl_vec<hardware::hidl_memory>& pools);

// Versioning

bool compliantWithV1_0(const V1_0::Capabilities& capabilities);
bool compliantWithV1_0(const V1_1::Capabilities& capabilities);
bool compliantWithV1_0(const V1_2::Capabilities& capabilities);
bool compliantWithV1_0(const V1_3::Capabilities& capabilities);
bool compliantWithV1_1(const V1_0::Capabilities& capabilities);
bool compliantWithV1_1(const V1_1::Capabilities& capabilities);
bool compliantWithV1_1(const V1_2::Capabilities& capabilities);
bool compliantWithV1_1(const V1_3::Capabilities& capabilities);
bool compliantWithV1_2(const V1_0::Capabilities& capabilities);
bool compliantWithV1_2(const V1_1::Capabilities& capabilities);
bool compliantWithV1_2(const V1_2::Capabilities& capabilities);
bool compliantWithV1_2(const V1_3::Capabilities& capabilities);
bool compliantWithV1_3(const V1_0::Capabilities& capabilities);
bool compliantWithV1_3(const V1_1::Capabilities& capabilities);
bool compliantWithV1_3(const V1_2::Capabilities& capabilities);
bool compliantWithV1_3(const V1_3::Capabilities& capabilities);

// If noncompliantOperations != nullptr, then
//     precondition: noncompliantOperations->empty()
//     postcondition: *noncompliantOperations consists of the indices of the noncompliant
//                    operations; if the compliance check fails for some reason
//                    other than a noncompliant operation,
//                    *noncompliantOperations consists of the indices of all operations
bool compliantWithV1_0(const V1_0::Model& model);
bool compliantWithV1_0(const V1_1::Model& model);
bool compliantWithV1_0(const V1_2::Model& model,
                       std::set<uint32_t>* noncompliantOperations = nullptr);
bool compliantWithV1_0(const V1_3::Model& model,
                       std::set<uint32_t>* noncompliantOperations = nullptr);
bool compliantWithV1_1(const V1_0::Model& model);
bool compliantWithV1_1(const V1_1::Model& model);
bool compliantWithV1_1(const V1_2::Model& model,
                       std::set<uint32_t>* noncompliantOperations = nullptr);
bool compliantWithV1_1(const V1_3::Model& model,
                       std::set<uint32_t>* noncompliantOperations = nullptr);
bool compliantWithV1_2(const V1_0::Model& model);
bool compliantWithV1_2(const V1_1::Model& model);
bool compliantWithV1_2(const V1_2::Model& model,
                       std::set<uint32_t>* noncompliantOperations = nullptr);
bool compliantWithV1_2(const V1_3::Model& model,
                       std::set<uint32_t>* noncompliantOperations = nullptr);

V1_0::ErrorStatus convertToV1_0(V1_0::ErrorStatus status);
V1_0::ErrorStatus convertToV1_0(V1_3::ErrorStatus status);
V1_3::ErrorStatus convertToV1_3(V1_0::ErrorStatus status);
V1_3::ErrorStatus convertToV1_3(V1_3::ErrorStatus status);

V1_0::Capabilities convertToV1_0(const V1_0::Capabilities& capabilities);
V1_0::Capabilities convertToV1_0(const V1_1::Capabilities& capabilities);
V1_0::Capabilities convertToV1_0(const V1_2::Capabilities& capabilities);
V1_0::Capabilities convertToV1_0(const V1_3::Capabilities& capabilities);
V1_1::Capabilities convertToV1_1(const V1_0::Capabilities& capabilities);
V1_1::Capabilities convertToV1_1(const V1_1::Capabilities& capabilities);
V1_1::Capabilities convertToV1_1(const V1_2::Capabilities& capabilities);
V1_1::Capabilities convertToV1_1(const V1_3::Capabilities& capabilities);
V1_2::Capabilities convertToV1_2(const V1_0::Capabilities& capabilities);
V1_2::Capabilities convertToV1_2(const V1_1::Capabilities& capabilities);
V1_2::Capabilities convertToV1_2(const V1_2::Capabilities& capabilities);
V1_2::Capabilities convertToV1_2(const V1_3::Capabilities& capabilities);
V1_3::Capabilities convertToV1_3(const V1_0::Capabilities& capabilities);
V1_3::Capabilities convertToV1_3(const V1_1::Capabilities& capabilities);
V1_3::Capabilities convertToV1_3(const V1_2::Capabilities& capabilities);
V1_3::Capabilities convertToV1_3(const V1_3::Capabilities& capabilities);

V1_0::Model convertToV1_0(const V1_0::Model& model);
V1_0::Model convertToV1_0(const V1_1::Model& model);
V1_0::Model convertToV1_0(const V1_2::Model& model);
V1_0::Model convertToV1_0(const V1_3::Model& model);
V1_1::Model convertToV1_1(const V1_0::Model& model);
V1_1::Model convertToV1_1(const V1_1::Model& model);
V1_1::Model convertToV1_1(const V1_2::Model& model);
V1_1::Model convertToV1_1(const V1_3::Model& model);
V1_2::Model convertToV1_2(const V1_0::Model& model);
V1_2::Model convertToV1_2(const V1_1::Model& model);
V1_2::Model convertToV1_2(const V1_2::Model& model);
V1_2::Model convertToV1_2(const V1_3::Model& model);
V1_3::Model convertToV1_3(const V1_0::Model& model);
V1_3::Model convertToV1_3(const V1_1::Model& model);
V1_3::Model convertToV1_3(const V1_2::Model& model);
V1_3::Model convertToV1_3(const V1_3::Model& model);

V1_0::OperationType uncheckedConvertToV1_0(V1_3::OperationType type);
V1_1::OperationType uncheckedConvertToV1_1(V1_3::OperationType type);
V1_2::OperationType uncheckedConvertToV1_2(V1_3::OperationType type);

V1_0::Operand convertToV1_0(const V1_2::Operand& operand);
V1_0::Operand convertToV1_0(const V1_3::Operand& operand);
V1_2::Operand convertToV1_2(const V1_0::Operand& operand);
V1_2::Operand convertToV1_2(const V1_3::Operand& operand);
V1_3::Operand convertToV1_3(const V1_0::Operand& operand);
V1_3::Operand convertToV1_3(const V1_2::Operand& operand);
V1_3::Operand convertToV1_3(const V1_3::Operand& operand);

hardware::hidl_vec<V1_0::Operand> convertToV1_0(const hardware::hidl_vec<V1_0::Operand>& operands);
hardware::hidl_vec<V1_0::Operand> convertToV1_0(const hardware::hidl_vec<V1_2::Operand>& operands);
hardware::hidl_vec<V1_0::Operand> convertToV1_0(const hardware::hidl_vec<V1_3::Operand>& operands);
hardware::hidl_vec<V1_2::Operand> convertToV1_2(const hardware::hidl_vec<V1_0::Operand>& operands);
hardware::hidl_vec<V1_2::Operand> convertToV1_2(const hardware::hidl_vec<V1_2::Operand>& operands);
hardware::hidl_vec<V1_2::Operand> convertToV1_2(const hardware::hidl_vec<V1_3::Operand>& operands);
hardware::hidl_vec<V1_3::Operand> convertToV1_3(const hardware::hidl_vec<V1_0::Operand>& operands);
hardware::hidl_vec<V1_3::Operand> convertToV1_3(const hardware::hidl_vec<V1_2::Operand>& operands);
hardware::hidl_vec<V1_3::Operand> convertToV1_3(const hardware::hidl_vec<V1_3::Operand>& operands);

bool compliantWithV1_0(const V1_0::Request& request);
bool compliantWithV1_0(const V1_3::Request& request);
bool compliantWithV1_2(const V1_3::Request& request);

V1_0::Request convertToV1_0(const V1_0::Request& request);
V1_0::Request convertToV1_0(const V1_3::Request& request);
V1_0::Request convertToV1_2(const V1_3::Request& request);
V1_3::Request convertToV1_3(const V1_0::Request& request);
V1_3::Request convertToV1_3(const V1_3::Request& request);

bool compliantWithV1_0(V1_0::OperandLifeTime lifetime);
bool compliantWithV1_0(V1_3::OperandLifeTime lifetime);
bool compliantWithV1_3(V1_0::OperandLifeTime lifetime);
bool compliantWithV1_3(V1_3::OperandLifeTime lifetime);

V1_0::OperandLifeTime convertToV1_0(V1_0::OperandLifeTime lifetime);
V1_0::OperandLifeTime convertToV1_0(V1_3::OperandLifeTime lifetime);
V1_3::OperandLifeTime convertToV1_3(V1_0::OperandLifeTime lifetime);
V1_3::OperandLifeTime convertToV1_3(V1_3::OperandLifeTime lifetime);

constexpr V1_3::Priority convertToHalPriority(int32_t priority) {
    switch (priority) {
        case ANEURALNETWORKS_PRIORITY_LOW:
            return V1_3::Priority::LOW;
        case ANEURALNETWORKS_PRIORITY_MEDIUM:
            return V1_3::Priority::MEDIUM;
        case ANEURALNETWORKS_PRIORITY_HIGH:
            return V1_3::Priority::HIGH;
    }
    LOG(FATAL) << "unrecognized priority: " << priority;
    return {};
}

// DEPRECATED. Use checked conversions from nnapi/hal/1.X/Conversions.h.
Capabilities::OperandPerformance uncheckedConvert(
        const V1_3::Capabilities::OperandPerformance& operandPerformance);
Capabilities::PerformanceInfo uncheckedConvert(const V1_0::PerformanceInfo& performanceInfo);
Capabilities uncheckedConvert(const V1_3::Capabilities& capabilities);
DataLocation uncheckedConvert(const V1_0::DataLocation& location);
ErrorStatus uncheckedConvert(V1_0::ErrorStatus status);
ErrorStatus uncheckedConvert(V1_3::ErrorStatus status);
Extension::OperandTypeInformation uncheckedConvert(const V1_2::Extension::OperandTypeInformation&);
Extension uncheckedConvert(const V1_2::Extension& extension);
hardware::hidl_vec<uint8_t> uncheckedConvert(const Operand::ExtensionParams& params);
MeasureTiming uncheckedConvert(V1_2::MeasureTiming measure);
SharedMemory uncheckedConvert(const hardware::hidl_memory& memory);
ExtensionNameAndPrefix uncheckedConvert(const V1_2::Model::ExtensionNameAndPrefix&);
Model::Subgraph uncheckedConvert(const V1_3::Subgraph& subgraph);
Model uncheckedConvert(const V1_3::Model& model);
Operand::ExtensionParams uncheckedConvert(const hardware::hidl_vec<uint8_t>& params);
Operand::ExtraParams uncheckedConvert(const V1_2::Operand::ExtraParams& params);
Operand::LifeTime uncheckedConvert(V1_3::OperandLifeTime lifetime);
Operand::SymmPerChannelQuantParams uncheckedConvert(const V1_2::SymmPerChannelQuantParams& params);
OperandType uncheckedConvert(V1_3::OperandType operandType);
Operand uncheckedConvert(const V1_3::Operand& operand);
OperationType uncheckedConvert(V1_3::OperationType operationType);
Operation uncheckedConvert(const V1_3::Operation& operation);
OptionalDuration uncheckedConvert(const V1_3::OptionalTimeoutDuration& timeoutDuration);
OutputShape uncheckedConvert(const V1_2::OutputShape& outputShape);
Request::Argument uncheckedConvert(const V1_0::RequestArgument& requestArgument);
Request::MemoryPool uncheckedConvert(const V1_3::Request::MemoryPool& memoryPool);
Request uncheckedConvert(const V1_3::Request& request);
std::vector<Extension> uncheckedConvert(const hardware::hidl_vec<V1_2::Extension>& extensions);
std::vector<SharedMemory> uncheckedConvert(
        const hardware::hidl_vec<hardware::hidl_memory>& memories);
std::vector<Model::Subgraph> uncheckedConvert(const hardware::hidl_vec<V1_3::Subgraph>& subgraphs);
std::vector<Operand> uncheckedConvert(const hardware::hidl_vec<V1_3::Operand>& operands);
std::vector<OutputShape> uncheckedConvert(
        const hardware::hidl_vec<V1_2::OutputShape>& outputShapes);
std::vector<Request::MemoryPool> uncheckedConvert(
        const hardware::hidl_vec<V1_3::Request::MemoryPool>& memoryPools);
Timing uncheckedConvert(const V1_2::Timing& timing);

// DEPRECATED. Use conversions from nnapi/hal/1.X/Conversions.h.
hardware::hidl_memory convertToV1_0(const SharedMemory& memory);
hardware::hidl_vec<hardware::hidl_memory> convertToV1_0(const std::vector<SharedMemory>& memories);
hardware::hidl_vec<uint8_t> convertToV1_0(const Model::OperandValues& operandValues);
hardware::hidl_vec<V1_2::OutputShape> convertToV1_2(const std::vector<OutputShape>& outputShapes);
hardware::hidl_vec<V1_3::BufferRole> convertToV1_3(const std::vector<BufferRole>& bufferRoles);
V1_0::DataLocation convertToV1_0(const DataLocation& location);
V1_0::ErrorStatus convertToV1_0(ErrorStatus status);
V1_0::RequestArgument convertToV1_0(const Request::Argument& requestArgument);
V1_1::ExecutionPreference convertToV1_1(ExecutionPreference preference);
V1_2::MeasureTiming convertToV1_2(MeasureTiming measure);
V1_2::Model::ExtensionNameAndPrefix convertToV1_2(const ExtensionNameAndPrefix&);
V1_2::Operand::ExtraParams convertToV1_2(const Operand::ExtraParams& params);
V1_2::OutputShape convertToV1_2(const OutputShape& outputShape);
V1_2::SymmPerChannelQuantParams convertToV1_2(const Operand::SymmPerChannelQuantParams& params);
V1_2::Timing convertToV1_2(const Timing& timing);
V1_3::BufferRole convertToV1_3(const BufferRole& bufferRole);
V1_3::ErrorStatus convertToV1_3(ErrorStatus status);
V1_3::Model convertToV1_3(const Model& model);
V1_3::Operand convertToV1_3(const Operand& operand);
V1_3::OperandLifeTime convertToV1_3(Operand::LifeTime lifetime);
V1_3::OperandType convertToV1_3(OperandType operandType);
V1_3::Operation convertToV1_3(const Operation& operation);
V1_3::OperationType convertToV1_3(OperationType operationType);
V1_3::OptionalTimeoutDuration convertToV1_3(const OptionalDuration& timeoutDuration);
V1_3::OptionalTimePoint convertToV1_3(const OptionalTimePoint& timePoint);
V1_3::Priority convertToV1_3(Priority priority);
V1_3::Request convertToV1_3(const Request& request);
V1_3::Request::MemoryPool convertToV1_3(const Request::MemoryPool& memoryPool);
V1_3::Subgraph convertToV1_3(const Model::Subgraph& model);

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_LEGACY_HAL_UTILS_H
