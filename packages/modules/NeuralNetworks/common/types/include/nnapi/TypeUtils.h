/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_TYPE_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_TYPE_UTILS_H

#include <android-base/expected.h>
#include <android-base/logging.h>
#include <android-base/macros.h>

#include <ostream>
#include <string>
#include <utility>
#include <vector>

#include "nnapi/OperandTypes.h"
#include "nnapi/OperationTypes.h"
#include "nnapi/Result.h"
#include "nnapi/Types.h"

namespace android::nn {

bool isExtension(OperandType type);
bool isExtension(OperationType type);

bool isNonExtensionScalar(OperandType operandType);

size_t getNonExtensionSize(OperandType operandType);

inline uint16_t getExtensionPrefix(uint32_t type) {
    return static_cast<uint16_t>(type >> kExtensionTypeBits);
}

inline uint16_t getTypeWithinExtension(uint32_t type) {
    return static_cast<uint16_t>(type & kTypeWithinExtensionMask);
}

std::optional<size_t> getNonExtensionSize(OperandType operandType, const Dimensions& dimensions);
std::optional<size_t> getNonExtensionSize(const Operand& operand);

bool tensorHasUnspecifiedDimensions(OperandType type, const Dimensions& dimensions);
bool tensorHasUnspecifiedDimensions(const Operand& operand);

size_t getOffsetFromInts(int lower, int higher);
std::pair<int32_t, int32_t> getIntsFromOffset(size_t offset);

Result<std::vector<uint32_t>> countNumberOfConsumers(size_t numberOfOperands,
                                                     const std::vector<nn::Operation>& operations);

// Combine two tensor dimensions, both may have unspecified dimensions or rank.
Result<Dimensions> combineDimensions(const Dimensions& lhs, const Dimensions& rhs);

// Returns the operandValues's size and a size for each pool in the provided model.
std::pair<size_t, std::vector<size_t>> getMemorySizes(const Model& model);

// Round up "size" to the nearest multiple of "multiple". "multiple" must be a power of 2.
size_t roundUp(size_t size, size_t multiple);

// Returns the alignment for data of the specified length.  It aligns object of length:
// 2, 3 on a 2 byte boundary,
// 4+ on a 4 byte boundary.
// We may want to have different alignments for tensors.
// TODO: This is arbitrary, more a proof of concept.  We need to determine what this should be.
//
// Note that Types.cpp ensures `new` has sufficient alignment for all alignments returned by this
// function. If this function is changed to return different alignments (e.g., 8 byte boundary
// alignment), the code check in Types.cpp similarly needs to be updated.
size_t getAlignmentForLength(size_t length);

// Make Capabilities provided three granularities of performance info.
Capabilities makeCapabilities(const Capabilities::PerformanceInfo& defaultInfo,
                              const Capabilities::PerformanceInfo& float32Info,
                              const Capabilities::PerformanceInfo& relaxedInfo);

// Set of output utility functions.
std::ostream& operator<<(std::ostream& os, const DeviceStatus& deviceStatus);
std::ostream& operator<<(std::ostream& os, const ExecutionPreference& executionPreference);
std::ostream& operator<<(std::ostream& os, const DeviceType& deviceType);
std::ostream& operator<<(std::ostream& os, const MeasureTiming& measureTiming);
std::ostream& operator<<(std::ostream& os, const OperandType& operandType);
std::ostream& operator<<(std::ostream& os, const Operand::LifeTime& lifetime);
std::ostream& operator<<(std::ostream& os, const OperationType& operationType);
std::ostream& operator<<(std::ostream& os, const Request::Argument::LifeTime& lifetime);
std::ostream& operator<<(std::ostream& os, const Priority& priority);
std::ostream& operator<<(std::ostream& os, const ErrorStatus& errorStatus);
std::ostream& operator<<(std::ostream& os, const FusedActivationFunc& activation);
std::ostream& operator<<(std::ostream& os, const OutputShape& outputShape);
std::ostream& operator<<(std::ostream& os, const Timing& timing);
std::ostream& operator<<(std::ostream& os, const Capabilities::PerformanceInfo& performanceInfo);
std::ostream& operator<<(std::ostream& os,
                         const Capabilities::OperandPerformance& operandPerformance);
std::ostream& operator<<(std::ostream& os,
                         const Capabilities::OperandPerformanceTable& operandPerformances);
std::ostream& operator<<(std::ostream& os, const Capabilities& capabilities);
std::ostream& operator<<(std::ostream& os,
                         const Extension::OperandTypeInformation& operandTypeInformation);
std::ostream& operator<<(std::ostream& os, const Extension& extension);
std::ostream& operator<<(std::ostream& os, const DataLocation& location);
std::ostream& operator<<(std::ostream& os,
                         const Operand::SymmPerChannelQuantParams& symmPerChannelQuantParams);
std::ostream& operator<<(std::ostream& os, const Operand::ExtraParams& extraParams);
std::ostream& operator<<(std::ostream& os, const Operand& operand);
std::ostream& operator<<(std::ostream& os, const Operation& operation);
std::ostream& operator<<(std::ostream& os, const SharedHandle& handle);
std::ostream& operator<<(std::ostream& os, const Memory& memory);
std::ostream& operator<<(std::ostream& os, const SharedMemory& memory);
std::ostream& operator<<(std::ostream& os, const MemoryPreference& memoryPreference);
std::ostream& operator<<(std::ostream& os, const Model::Subgraph& subgraph);
std::ostream& operator<<(std::ostream& os, const Model::OperandValues& operandValues);
std::ostream& operator<<(std::ostream& os, const ExtensionNameAndPrefix& extensionNameAndPrefix);
std::ostream& operator<<(std::ostream& os, const Model& model);
std::ostream& operator<<(std::ostream& os, const BufferDesc& bufferDesc);
std::ostream& operator<<(std::ostream& os, const BufferRole& bufferRole);
std::ostream& operator<<(std::ostream& os, const Request::Argument& requestArgument);
std::ostream& operator<<(std::ostream& os, const Request::MemoryPool& memoryPool);
std::ostream& operator<<(std::ostream& os, const Request& request);
std::ostream& operator<<(std::ostream& os, const SyncFence::FenceState& fenceState);
std::ostream& operator<<(std::ostream& os, const TimePoint& timePoint);
std::ostream& operator<<(std::ostream& os, const OptionalTimePoint& optionalTimePoint);
std::ostream& operator<<(std::ostream& os, const Duration& timeoutDuration);
std::ostream& operator<<(std::ostream& os, const OptionalDuration& optionalTimeoutDuration);
std::ostream& operator<<(std::ostream& os, const Version::Level& versionLevel);
std::ostream& operator<<(std::ostream& os, const Version& version);

bool operator==(const Timing& a, const Timing& b);
bool operator!=(const Timing& a, const Timing& b);
bool operator==(const Capabilities::PerformanceInfo& a, const Capabilities::PerformanceInfo& b);
bool operator!=(const Capabilities::PerformanceInfo& a, const Capabilities::PerformanceInfo& b);
bool operator==(const Capabilities::OperandPerformance& a,
                const Capabilities::OperandPerformance& b);
bool operator!=(const Capabilities::OperandPerformance& a,
                const Capabilities::OperandPerformance& b);
bool operator==(const Capabilities& a, const Capabilities& b);
bool operator!=(const Capabilities& a, const Capabilities& b);
bool operator==(const Extension::OperandTypeInformation& a,
                const Extension::OperandTypeInformation& b);
bool operator!=(const Extension::OperandTypeInformation& a,
                const Extension::OperandTypeInformation& b);
bool operator==(const Extension& a, const Extension& b);
bool operator!=(const Extension& a, const Extension& b);
bool operator==(const MemoryPreference& a, const MemoryPreference& b);
bool operator!=(const MemoryPreference& a, const MemoryPreference& b);
bool operator==(const Operand::SymmPerChannelQuantParams& a,
                const Operand::SymmPerChannelQuantParams& b);
bool operator!=(const Operand::SymmPerChannelQuantParams& a,
                const Operand::SymmPerChannelQuantParams& b);
bool operator==(const Operand& a, const Operand& b);
bool operator!=(const Operand& a, const Operand& b);
bool operator==(const Operation& a, const Operation& b);
bool operator!=(const Operation& a, const Operation& b);
bool operator==(const Version& a, const Version& b);
bool operator!=(const Version& a, const Version& b);

inline std::string toString(uint32_t obj) {
    return std::to_string(obj);
}

template <typename A, typename B>
std::string toString(const std::pair<A, B>& pair) {
    std::ostringstream oss;
    oss << "(" << pair.first << ", " << pair.second << ")";
    return oss.str();
}

template <typename Type>
std::string toString(const std::vector<Type>& vec) {
    std::string os = "[";
    for (size_t i = 0; i < vec.size(); ++i) {
        os += (i == 0 ? "" : ", ") + toString(vec[i]);
    }
    return os += "]";
}

/* IMPORTANT: if you change the following list, don't
 * forget to update the corresponding 'tags' table in
 * the initVlogMask() function implemented in Utils.cpp.
 */
enum VLogFlags { MODEL = 0, COMPILATION, EXECUTION, CPUEXE, MANAGER, DRIVER, MEMORY };

#define VLOG_IS_ON(TAG) ((vLogMask & (1 << (TAG))) != 0)

#define VLOG(TAG)                 \
    if (LIKELY(!VLOG_IS_ON(TAG))) \
        ;                         \
    else                          \
        LOG(INFO)

extern int vLogMask;
void initVLogMask();

// The NN_RET_CHECK family of macros defined below is similar to the CHECK family defined in
// system/libbase/include/android-base/logging.h
//
// The difference is that NN_RET_CHECK macros use LOG(ERROR) instead of LOG(FATAL)
// and return false instead of aborting.

// Logs an error and returns false. Append context using << after. For example:
//
//   NN_RET_CHECK_FAIL() << "Something went wrong";
//
// The containing function must return a bool or a base::expected (including nn::Result,
// nn::GeneralResult, and nn::ExecutionResult).
#define NN_RET_CHECK_FAIL()                       \
    return ::android::nn::NnRetCheckErrorStream() \
           << "NN_RET_CHECK failed (" << __FILE__ << ":" << __LINE__ << "): "

// Logs an error and returns false if condition is false. Extra logging can be appended using <<
// after. For example:
//
//   NN_RET_CHECK(false) << "Something went wrong";
//
// The containing function must return a bool or a base::expected (including nn::Result,
// nn::GeneralResult, and nn::ExecutionResult).
#define NN_RET_CHECK(condition) \
    while (UNLIKELY(!(condition))) NN_RET_CHECK_FAIL() << #condition << " "

// Helper for NN_CHECK_xx(x, y) macros.
#define NN_RET_CHECK_OP(LHS, RHS, OP)                                                       \
    for (auto _values = ::android::base::MakeEagerEvaluator(LHS, RHS);                      \
         UNLIKELY(!(_values.lhs.v OP _values.rhs.v));                                       \
         /* empty */)                                                                       \
    NN_RET_CHECK_FAIL()                                                                     \
            << #LHS << " " << #OP << " " << #RHS << " (" << #LHS << " = "                   \
            << ::android::base::LogNullGuard<decltype(_values.lhs.v)>::Guard(_values.lhs.v) \
            << ", " << #RHS << " = "                                                        \
            << ::android::base::LogNullGuard<decltype(_values.rhs.v)>::Guard(_values.rhs.v) \
            << ") "

// Logs an error and returns false if a condition between x and y does not hold. Extra logging can
// be appended using << after. For example:
//
//   NN_RET_CHECK_EQ(a, b) << "Something went wrong";
//
// The values must implement the appropriate comparison operator as well as
// `operator<<(std::ostream&, ...)`.
// The containing function must return a bool or a base::expected (including nn::Result,
// nn::GeneralResult, and nn::ExecutionResult).
#define NN_RET_CHECK_EQ(x, y) NN_RET_CHECK_OP(x, y, ==)
#define NN_RET_CHECK_NE(x, y) NN_RET_CHECK_OP(x, y, !=)
#define NN_RET_CHECK_LE(x, y) NN_RET_CHECK_OP(x, y, <=)
#define NN_RET_CHECK_LT(x, y) NN_RET_CHECK_OP(x, y, <)
#define NN_RET_CHECK_GE(x, y) NN_RET_CHECK_OP(x, y, >=)
#define NN_RET_CHECK_GT(x, y) NN_RET_CHECK_OP(x, y, >)

// Ensure that every user of NnRetCheckErrorStream is linked to the
// correct instance, using the correct LOG_TAG
namespace {

// A wrapper around an error message that can be implicitly converted to bool (always evaluates to
// false) and logs via LOG(ERROR) or base::expected (always evaluates to base::unexpected). Used to
// implement stream logging in NN_RET_CHECK.
class NnRetCheckErrorStream {
    DISALLOW_COPY_AND_ASSIGN(NnRetCheckErrorStream);

   public:
    constexpr NnRetCheckErrorStream() = default;

    template <typename T>
    NnRetCheckErrorStream& operator<<(const T& value) {
        (*mBuffer) << value;
        return *this;
    }

    ~NnRetCheckErrorStream() {
        if (mBuffer.has_value()) {
            LOG(ERROR) << mBuffer->str();
        }
    }

    constexpr operator bool() const { return false; }  // NOLINT(google-explicit-constructor)

    template <typename T, typename E>
    constexpr operator base::expected<T, E>() {  // NOLINT(google-explicit-constructor)
        auto result = base::unexpected<E>(std::move(mBuffer)->str());
        mBuffer.reset();
        return result;
    }

    // This is needed because conversion to Result<int> is ambiguous
    // due to the above bool() operator overload
    operator Result<int>() {  // NOLINT(google-explicit-constructor)
        auto result = base::unexpected(std::move(mBuffer)->str());
        mBuffer.reset();
        return result;
    }

   private:
    std::optional<std::ostringstream> mBuffer = std::ostringstream{};
};

}  // namespace

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_TYPE_UTILS_H
