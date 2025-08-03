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
// This file contains pre-canonical-types utility code and does not includes HAL
// utilities. LegacyHalUtils.h is a superset of these utilities that includes
// HAL utilities.

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_LEGACY_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_LEGACY_UTILS_H

#include <android-base/logging.h>
#include <nnapi/TypeUtils.h>
#include <nnapi/Types.h>

#include <functional>
#include <tuple>
#include <utility>
#include <vector>

#include "NeuralNetworks.h"
#include "OperationResolver.h"
#include "nnapi/TypeUtils.h"
#include "nnapi/Types.h"

namespace android {
namespace nn {

// The number of data types (OperandCode) defined in NeuralNetworksTypes.h.
const int kNumberOfDataTypes = 16;

// The number of operation types (OperationCode) defined in NeuralNetworksTypes.h.
const int kNumberOfOperationTypes = 106;

#ifdef NN_EXPERIMENTAL_FEATURE
const int kNumberOfExperimentalOperationTypes = 1;
#endif  // NN_EXPERIMENTAL_FEATURE

static_assert(kNumberOfOperationTypes == BuiltinOperationResolver::kNumberOfOperationTypes);

// The number of execution preferences defined in NeuralNetworks.h.
const int kNumberOfPreferences = 3;

// The number of data types (OperandCode) defined in NeuralNetworksOEM.h.
const int kNumberOfDataTypesOEM = 2;

// The number of operation types (OperationCode) defined in NeuralNetworksOEM.h.
const int kNumberOfOperationTypesOEM = 1;

// The lowest number assigned to any OEM Code in NeuralNetworksOEM.h.
const int kOEMCodeBase = 10000;

#ifdef NN_DEBUGGABLE
#define SHOW_IF_DEBUG(msg) msg
#else
#define SHOW_IF_DEBUG(msg) ""
#endif

#define NN_RETURN_IF_ERROR(expr)                      \
    do {                                              \
        int _errorCode = (expr);                      \
        if (_errorCode != ANEURALNETWORKS_NO_ERROR) { \
            return _errorCode;                        \
        }                                             \
    } while (0)

enum class HalVersion : int32_t {
    UNKNOWN,
    V1_0,
    V1_1,
    V1_2,
    V1_3,
    AIDL_V1,
    AIDL_V2,
    AIDL_UNSTABLE,
    // TODO(b/207721221): Add AIDL support to TestPartitioning so that LATEST can be set to AIDL
    //  version.
    LATEST = V1_3,
};

std::ostream& operator<<(std::ostream& os, const HalVersion& halVersion);

// Make a Duration from a duration in nanoseconds. If the value exceeds the max duration, return the
// maximum expressible duration.
Duration makeTimeoutDuration(uint64_t nanoseconds);

// Make a Duration from a duration in nanoseconds. If the value exceeds the max duration, return the
// maximum expressible duration. If nanoseconds == -1, the duration is omitted. Precondition:
// nanoseconds >= -1
OptionalDuration makeTimeoutDuration(int64_t nanoseconds);

// Make a deadline from a duration. If the sum of the current time and the
// duration exceeds the max time, return a time point holding the maximum
// expressible time.
TimePoint makeDeadline(Duration duration);

inline TimePoint makeDeadline(uint64_t duration) {
    return makeDeadline(makeTimeoutDuration(duration));
}

// Convenience function. If the duration is provided, this function creates a
// deadline using makeDeadline. If the duration is not provided, this function
// returns std::nullopt.
inline OptionalTimePoint makeDeadline(OptionalDuration duration) {
    return duration.has_value() ? std::make_optional(makeDeadline(*duration)) : OptionalTimePoint{};
}
inline OptionalTimePoint makeDeadline(std::optional<uint64_t> duration) {
    return duration.has_value() ? std::make_optional(makeDeadline(*duration)) : OptionalTimePoint{};
}
inline OptionalTimePoint makeDeadline(int64_t duration) {
    return makeDeadline(makeTimeoutDuration(duration));
}

// Returns true if the deadline has passed. Returns false if either the deadline
// has not been exceeded or if the deadline is not present.
bool hasDeadlinePassed(const OptionalTimePoint& deadline);

// Returns true if an operand type is an extension type.
bool isExtensionOperandType(OperandType type);

// Returns true if an operation type is an extension type.
bool isExtensionOperationType(OperationType type);

// Returns the amount of space needed to store a value of the specified
// dimensions and type. For a tensor with unspecified rank or at least one
// unspecified dimension, returns zero.
//
// Aborts if the specified type is an extension type.
// Aborts if the size would overflow the return type.
//
// See also TypeManager::getSizeOfData(OperandType, const std::vector<uint32_t>&).
uint32_t nonExtensionOperandSizeOfData(OperandType type, const std::vector<uint32_t>& dimensions);

// Returns the amount of space needed to store a value of the dimensions and
// type of this operand. For a tensor with unspecified rank or at least one
// unspecified dimension, returns zero.
//
// Aborts if the specified type is an extension type.
// Aborts if the size would overflow the return type.
//
// See also TypeManager::getSizeOfData(const Operand&).
inline uint32_t nonExtensionOperandSizeOfData(const Operand& operand) {
    return nonExtensionOperandSizeOfData(operand.type, operand.dimensions);
}

// Returns the amount of space needed to store a value of the specified
// dimensions and element size. For a tensor with unspecified rank or at least
// one unspecified dimension, returns zero.
//
// Aborts if the size would overflow the return type.
//
// See also TypeManager::getSizeOfData(const Operand&).
uint32_t sizeOfTensorData(uint32_t sizeOfElement, const std::vector<uint32_t>& dimensions);

// Returns true if the amount of space needed to store a value of the specified
// dimensions and element size overflows the uint32_t type.
//
// Aborts if the specified type is an extension type.
//
// See also TypeManager::sizeOfDataOverflowsUInt32(OperandType, const std::vector<uint32_t>&).
bool nonExtensionOperandSizeOfDataOverflowsUInt32(OperandType type,
                                                  const std::vector<uint32_t>& dimensions);

// Returns true if the amount of space needed to store a value of the specified
// dimensions and element size overflows the uint32_t type.
//
// See also TypeManager::sizeOfDataOverflowsUInt32(OperandType, const std::vector<uint32_t>&).
bool sizeOfTensorDataOverflowsUInt32(uint32_t elementSize, const std::vector<uint32_t>& dimensions);

// Returns true if a non-extension operand type is a scalar type.
//
// Aborts if the specified type is an extension type.
//
// See also TypeManager::isTensorType(OperandType).
bool nonExtensionOperandTypeIsScalar(int type);

// Whether an operand of tensor type has unspecified dimensions.
//
// Undefined behavior if the operand type is a scalar type.
bool tensorHasUnspecifiedDimensions(int type, const uint32_t* dim, uint32_t dimCount);
bool tensorHasUnspecifiedDimensions(OperandType type, const Dimensions& dimensions);
bool tensorHasUnspecifiedDimensions(const ANeuralNetworksOperandType* type);

// Returns the number of padding bytes needed to align data starting at `index` with `length` number
// of bytes such that `index` + returned number of padding bytes is aligned. Refer to
// `getAlignmentForLength` for more information on alignment (such as what the current alignments
// are for different data lengths).
uint32_t alignBytesNeeded(uint32_t index, size_t length);

// Does a detailed LOG(INFO) of the model
void logModelToInfo(const Model& model);

inline bool validCode(uint32_t codeCount, uint32_t codeCountOEM, uint32_t code) {
    return (code < codeCount) || (code >= kOEMCodeBase && (code - kOEMCodeBase) < codeCountOEM);
}

// Validates an operand type.
//
// extensionOperandTypeInfo must be nullptr iff the type is not an extension type.
//
// If allowPartial is true, the dimensions may be underspecified.
int validateOperandType(const ANeuralNetworksOperandType& type,
                        const Extension::OperandTypeInformation* const extensionOperandTypeInfo,
                        const char* tag, bool allowPartial);
int validateOperandList(uint32_t count, const uint32_t* list, uint32_t operandCount,
                        const char* tag);

// A set of functions to help validate models containing IF or WHILE operations.
struct SubgraphValidationHelper {
    // Checks if a given operand is a SUBGRAPH operand with a valid offset.
    std::function<bool(const Operand&)> isValidSubgraphReference;
    // Gets the input count of a subgraph referenced by a given operand.
    std::function<uint32_t(const Operand&)> getSubgraphInputCount;
    // Gets the output count of a subgraph referenced by a given operand.
    std::function<uint32_t(const Operand&)> getSubgraphOutputCount;
    // Gets the specified input operand of a subgraph referenced by a given operand.
    std::function<const Operand*(const Operand&, uint32_t)> getSubgraphInputOperand;
    // Gets the specified output operand of a subgraph referenced by a given operand.
    std::function<const Operand*(const Operand&, uint32_t)> getSubgraphOutputOperand;
    // Whether control flow operations with inner or outer input or output
    // operands of unknown size are allowed.
    bool allowControlFlowOperationWithOperandOfUnknownSize;
};

// Returns ANEURALNETWORKS_NO_ERROR if the corresponding operation is defined and can handle the
// provided operand types in the given HAL version, otherwise returns ANEURALNETWORKS_BAD_DATA.
// The last argument is only used for validating IF and WHILE operations.
int validateOperation(ANeuralNetworksOperationType opType, uint32_t inputCount,
                      const uint32_t* inputIndexes, uint32_t outputCount,
                      const uint32_t* outputIndexes, const std::vector<Operand>& operands,
                      HalVersion halVersion, const SubgraphValidationHelper& helper);

inline size_t getSizeFromInts(int lower, int higher) {
    return (uint32_t)(lower) + ((uint64_t)(uint32_t)(higher) << 32);
}

// Convert ANEURALNETWORKS_* result code to ErrorStatus.
// Not guaranteed to be a 1-to-1 mapping.
ErrorStatus convertResultCodeToErrorStatus(int resultCode);

// Convert ErrorStatus to ANEURALNETWORKS_* result code.
// Not guaranteed to be a 1-to-1 mapping.
int convertErrorStatusToResultCode(ErrorStatus status);

// Convert execution results to runtime format. Additionally checks that the
// returned results abide by the HAL specification, and logs an error if the
// result violates the specification.
std::tuple<int, std::vector<OutputShape>, Timing> getExecutionResult(
        ErrorStatus status, std::vector<OutputShape> outputShapes, Timing timing);

constexpr Priority convertToCanonicalPriority(int32_t priority) {
    switch (priority) {
        case ANEURALNETWORKS_PRIORITY_LOW:
            return Priority::LOW;
        case ANEURALNETWORKS_PRIORITY_MEDIUM:
            return Priority::MEDIUM;
        case ANEURALNETWORKS_PRIORITY_HIGH:
            return Priority::HIGH;
    }
    LOG(FATAL) << "unrecognized priority: " << priority;
    return {};
}

// The function syncWait() has the same semantics as the system function
// ::sync_wait(), except that the syncWait() return value is semantically
// richer.  The timeout parameter is in msecs.
enum class FenceState {
    ACTIVE,    // fence has not been signaled
    SIGNALED,  // fence has been signaled
    ERROR,     // fence has been placed in the error state
    UNKNOWN,   // either bad argument passed to syncWait(), or internal error
};
FenceState syncWait(int fd, int timeout);

#ifdef NN_DEBUGGABLE
uint32_t getProp(const char* str, uint32_t defaultValue = 0);
#endif  // NN_DEBUGGABLE

struct ApiVersion {
    Version canonical;
    int64_t featureLevel;
};

constexpr auto kHalVersionV1_0ToApi = ApiVersion{.canonical = kVersionFeatureLevel1,
                                                 .featureLevel = ANEURALNETWORKS_FEATURE_LEVEL_1};
constexpr auto kHalVersionV1_1ToApi = ApiVersion{.canonical = kVersionFeatureLevel2,
                                                 .featureLevel = ANEURALNETWORKS_FEATURE_LEVEL_2};
constexpr auto kHalVersionV1_2ToApi = ApiVersion{.canonical = kVersionFeatureLevel3,
                                                 .featureLevel = ANEURALNETWORKS_FEATURE_LEVEL_3};
constexpr auto kHalVersionV1_3ToApi = ApiVersion{.canonical = kVersionFeatureLevel4,
                                                 .featureLevel = ANEURALNETWORKS_FEATURE_LEVEL_4};

// Utility that measures time period, in nanoseconds, from creation
// to destruction and stores result in the supplied memory location
// on destruction
struct [[nodiscard]] TimeNanoMeasurer {
    TimePoint start;
    uint64_t* saveAt;

    explicit TimeNanoMeasurer(uint64_t* saveAt) : start(Clock::now()), saveAt(saveAt) {}
    ~TimeNanoMeasurer() { *saveAt = currentDuration(start); }
    DISALLOW_COPY_AND_ASSIGN(TimeNanoMeasurer);

    static inline uint64_t currentDuration(const TimePoint& start) {
        auto end = Clock::now();
        return std::chrono::duration_cast<std::chrono::nanoseconds>(end - start).count();
    }
};

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_LEGACY_UTILS_H
