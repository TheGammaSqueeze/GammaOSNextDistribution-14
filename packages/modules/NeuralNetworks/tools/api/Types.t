%% template file for generating Types.h.
%% see README.md.
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_TYPES_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_TYPES_H

#include <android-base/chrono_utils.h>
#include <android-base/expected.h>
#include <android-base/unique_fd.h>

#include <array>
#include <chrono>
#include <functional>
#include <limits>
#include <memory>
#include <optional>
#include <string>
#include <type_traits>
#include <utility>
#include <variant>
#include <vector>

#include "nnapi/OperandTypes.h"
#include "nnapi/OperationTypes.h"
#include "nnapi/Result.h"

// Forward declare AHardwareBuffer
extern "C" typedef struct AHardwareBuffer AHardwareBuffer;

namespace android::nn {

// Forward declarations

class IBuffer;
class IBurst;
class IDevice;
class IExecution;
class IPreparedModel;
struct Memory;

// Constants

constexpr float kDefaultExecTime = std::numeric_limits<float>::max();
constexpr float kDefaultPowerUsage = std::numeric_limits<float>::max();
constexpr uint32_t kByteSizeOfCacheToken = 32;
constexpr uint32_t kMaxNumberOfCacheFiles = 32;

%insert ExtensionTypeEncoding

constexpr uint32_t kDefaultRequestMemoryAlignment = 64;
constexpr uint32_t kDefaultRequestMemoryPadding = 64;
constexpr uint32_t kMinMemoryAlignment = alignof(std::max_align_t);
constexpr uint32_t kMinMemoryPadding = 1;
constexpr auto kLoopTimeoutDefault = std::chrono::seconds{2};
constexpr auto kLoopTimeoutMaximum = std::chrono::seconds{15};

// Aliases

using SharedBuffer = std::shared_ptr<const IBuffer>;
using SharedBurst = std::shared_ptr<const IBurst>;
using SharedDevice = std::shared_ptr<const IDevice>;
using SharedExecution = std::shared_ptr<const IExecution>;
using SharedMemory = std::shared_ptr<const Memory>;
using SharedPreparedModel = std::shared_ptr<const IPreparedModel>;

// Canonical types

%insert DeviceStatus

%insert ExecutionPreference

%insert DeviceType

%insert MeasureTiming

%insert Priority

// TODO: Should more errors from NeuralNetworks.h be incorporated? The left name shows errors that
// appear in NeuralNetworks.h but not in the HAL, and the right column shows what these values could
// map to:
// * OUT_OF_MEMORY ==> GENERAL_FAILURE / RESOURCE_EXHAUSTED_*
// * INCOMPLETE ==> GENERAL_FAILURE
// * UNEXPECTED_NULL ==> INVALID_ARGUMENT
// * UNMAPPABLE ==> GENERAL_FAILURE
// * BAD_STATE ==> INVALID_ARGUMENT
enum class ErrorStatus {
    NONE = 0,
    DEVICE_UNAVAILABLE = 1,
    GENERAL_FAILURE = 2,
    OUTPUT_INSUFFICIENT_SIZE = 3,
    INVALID_ARGUMENT = 4,
    MISSED_DEADLINE_TRANSIENT = 5,
    MISSED_DEADLINE_PERSISTENT = 6,
    RESOURCE_EXHAUSTED_TRANSIENT = 7,
    RESOURCE_EXHAUSTED_PERSISTENT = 8,
    DEAD_OBJECT = 10000,
};

struct GeneralError {
    // NOLINTNEXTLINE(google-explicit-constructor)
    /*implicit*/ GeneralError(std::string message = {},
                              ErrorStatus code = ErrorStatus::GENERAL_FAILURE);

    std::string message;
    ErrorStatus code;
};

template <typename Type>
using GeneralResult = base::expected<Type, GeneralError>;

%insert FusedActivationFunc

using Dimension = uint32_t;
using Dimensions = std::vector<Dimension>;

using CacheToken = std::array<uint8_t, kByteSizeOfCacheToken>;

%insert OutputShape

struct ExecutionError {
    // NOLINTNEXTLINE(google-explicit-constructor)
    /*implicit*/ ExecutionError(std::string message = {},
                                ErrorStatus code = ErrorStatus::GENERAL_FAILURE,
                                std::vector<OutputShape> outputShapes = {});

    // NOLINTNEXTLINE(google-explicit-constructor)
    /*implicit*/ ExecutionError(GeneralError error);

    std::string message;
    ErrorStatus code;
    // OutputShapes for code == OUTPUT_INSUFFICIENT_SIZE
    std::vector<OutputShape> outputShapes;
};

template <typename Type>
using ExecutionResult = base::expected<Type, ExecutionError>;

%insert Capabilities

%insert Extension

%insert Operation

%insert DataLocation

%insert Operand

using Handle = base::unique_fd;
using SharedHandle = std::shared_ptr<const Handle>;

struct Memory {
    struct Ashmem {
        base::unique_fd fd;
        size_t size;
    };

    struct Fd {
        size_t size;
        int prot;
        base::unique_fd fd;
        size_t offset;
    };

    // RAII wrapper for AHardwareBuffer
    struct HardwareBuffer {
        using Deleter = std::add_pointer_t<void(AHardwareBuffer*)>;
        using Handle = std::unique_ptr<AHardwareBuffer, Deleter>;
        Handle handle;
    };

    struct Unknown {
        struct Handle {
            std::vector<base::unique_fd> fds;
            std::vector<int> ints;
        };
        Handle handle;
        size_t size;
        std::string name;
    };

    std::variant<Ashmem, Fd, HardwareBuffer, Unknown> handle;
};

%insert ExtensionNameAndPrefix

%insert Model

%insert BufferDesc

%insert BufferRole

%insert Request

// Representation of sync_fence.
class SyncFence {
   public:
    static SyncFence createAsSignaled();
    static SyncFence create(base::unique_fd fd);
    static Result<SyncFence> create(SharedHandle syncFence);

    // The function syncWait() has the same semantics as the system function
    // ::sync_wait(), except that the syncWait() return value is semantically
    // richer.
    enum class FenceState {
        ACTIVE,    // fence has not been signaled
        SIGNALED,  // fence has been signaled
        ERROR,     // fence has been placed in the error state
        UNKNOWN,   // either bad argument passed to syncWait(), or internal error
    };
    using Timeout = std::chrono::duration<int, std::milli>;
    using OptionalTimeout = std::optional<Timeout>;

    FenceState syncWait(OptionalTimeout optionalTimeout) const;

    SharedHandle getSharedHandle() const;
    bool hasFd() const;
    int getFd() const;

   private:
    explicit SyncFence(SharedHandle syncFence);

    SharedHandle mSyncFence;
};

using Clock = base::boot_clock;

using Duration = std::chrono::nanoseconds;
using OptionalDuration = std::optional<Duration>;

using TimePoint = std::chrono::time_point<Clock, Duration>;
using OptionalTimePoint = std::optional<TimePoint>;

%insert Timing

// Returns status, timingLaunched, timingFenced
using ExecuteFencedInfoCallback = std::function<GeneralResult<std::pair<Timing, Timing>>()>;

// Version is a tuple that contains what NNAPI feature level is supported/required and whether
// runtime-only features are supported/required.
struct Version {
    enum class Level : uint8_t {
        FEATURE_LEVEL_1,
        FEATURE_LEVEL_2,
        FEATURE_LEVEL_3,
        FEATURE_LEVEL_4,
        FEATURE_LEVEL_5,
        FEATURE_LEVEL_6,
        FEATURE_LEVEL_7,
        FEATURE_LEVEL_8,
#ifdef NN_EXPERIMENTAL_FEATURE
        FEATURE_LEVEL_EXPERIMENTAL,
#endif  // NN_EXPERIMENTAL_FEATURE
    };

    Level level;
    bool runtimeOnlyFeatures = false;
};

constexpr auto kVersionFeatureLevel1 = Version{.level = Version::Level::FEATURE_LEVEL_1};
constexpr auto kVersionFeatureLevel2 = Version{.level = Version::Level::FEATURE_LEVEL_2};
constexpr auto kVersionFeatureLevel3 = Version{.level = Version::Level::FEATURE_LEVEL_3};
constexpr auto kVersionFeatureLevel4 = Version{.level = Version::Level::FEATURE_LEVEL_4};
constexpr auto kVersionFeatureLevel5 = Version{.level = Version::Level::FEATURE_LEVEL_5};
constexpr auto kVersionFeatureLevel6 = Version{.level = Version::Level::FEATURE_LEVEL_6};
constexpr auto kVersionFeatureLevel7 = Version{.level = Version::Level::FEATURE_LEVEL_7};
constexpr auto kVersionFeatureLevel8 = Version{.level = Version::Level::FEATURE_LEVEL_8};
#ifdef NN_EXPERIMENTAL_FEATURE
constexpr auto kVersionFeatureLevelExperimental =
        Version{.level = Version::Level::FEATURE_LEVEL_EXPERIMENTAL};
#endif  // NN_EXPERIMENTAL_FEATURE

// Describes the memory preference of an operand.
struct MemoryPreference {
    // Must be a power of 2.
    // For pointer buffers, the alignment is satisfied if the address of the pointer is a multiple
    // of the "alignment" value. For memory pools, the alignment is satisfied if the offset of the
    // sub-region specified by DataLocation is a multiple of the "alignment" value.
    uint32_t alignment;
    // Must be a power of 2.
    // For both pointer buffers and memory pools, the padding is satisfied if the padded length is
    // greater than or equal to the raw size of the operand (i.e. the size of an element multiplied
    // by the number of elements) rounding up to a multiple of the "padding" value. In DataLocation,
    // the padded length equals to the sum of the length and padding fields.
    uint32_t padding;
};

/**
 * A type that is used to represent a token / byte array data pair.
 */
struct TokenValuePair {
    /**
     * A 32bit integer token. The token is created by combining the
     * extension prefix and enum defined within the extension. Of the 32 bits in the token, the high
     * kExtensionPrefixBits bits is the extension prefix and the low kExtensionTypeBits bits
     * represents the enum within the extension.
     *
     * For example, if a token value is 0x7AAA000B and corresponding {@link ExtensionNameAndPrefix}
     * contains an entry with prefix=0x7AAA and name="vendor.test.test_extension", then the token
     * should be interpreted as the enum value 0x000B of the extension named
     * vendor.test.test_extension.
     */
    int32_t token;
    /**
     * A byte array containing the raw data.
     */
    std::vector<uint8_t> value;
};

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_TYPES_H
