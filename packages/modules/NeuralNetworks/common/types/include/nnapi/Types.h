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

/**
 * Numeric values of extension operand and operation types have the
 * following structure:
 * - 16 high bits represent the "prefix", which corresponds uniquely to the
 *   extension name.
 * - 16 low bits represent the type ID within the extension.
 */
constexpr uint8_t kExtensionTypeBits = 16;
constexpr uint8_t kExtensionPrefixBits = 16;
constexpr uint32_t kTypeWithinExtensionMask = 0xFFFF;

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

/**
 * Status of a device.
 */
enum class DeviceStatus {
    AVAILABLE = 0,
    BUSY = 1,
    OFFLINE = 2,
    UNKNOWN = 3,
};

/**
 * Execution preferences.
 */
enum class ExecutionPreference {
    /**
     * Prefer executing in a way that minimizes battery drain.
     * This is desirable for compilations that will be executed often.
     */
    LOW_POWER = 0,
    /**
     * Prefer returning a single answer as fast as possible, even if this causes
     * more power consumption.
     */
    FAST_SINGLE_ANSWER = 1,
    /**
     * Prefer maximizing the throughput of successive frames, for example when
     * processing successive frames coming from the camera.
     */
    SUSTAINED_SPEED = 2,
    DEFAULT = FAST_SINGLE_ANSWER,
};

/**
 * Device types.
 *
 * The type of NNAPI device.
 */
enum class DeviceType {
    /** The device type cannot be provided. */
    UNKNOWN = 0,
    /** The device does not fall into any category below. */
    OTHER = 1,
    /** The device runs NNAPI models on single or multi-core CPU. */
    CPU = 2,
    /** The device can run NNAPI models and also accelerate graphics APIs such
     * as OpenGL ES and Vulkan. */
    GPU = 3,
    /** Dedicated accelerator for Machine Learning workloads. */
    ACCELERATOR = 4,
};

/**
 * Specifies whether or not to measure timing information during execution.
 */
enum class MeasureTiming {
    NO = 0,
    YES = 1,
};

/**
 * Priority given to a prepared model for execution.
 */
enum class Priority {
    LOW = 0,
    MEDIUM = 1,
    HIGH = 2,
    DEFAULT = MEDIUM,
};

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

/**
 * Fused activation function types.
 */
enum class FusedActivationFunc : int32_t {
    /** NO fused activation function. */
    NONE = 0,
    /** Fused ReLU activation function. */
    RELU = 1,
    /** Fused ReLU1 activation function. */
    RELU1 = 2,
    /** Fused ReLU6 activation function. */
    RELU6 = 3,
};

using Dimension = uint32_t;
using Dimensions = std::vector<Dimension>;

using CacheToken = std::array<uint8_t, kByteSizeOfCacheToken>;

/**
 * Describes the shape information of an output operand after execution.
 */
struct OutputShape {
    /**
     * Dimensions of the operand.
     */
    std::vector<uint32_t> dimensions;

    /**
     * Whether the provided buffer size is sufficient for the output.
     */
    bool isSufficient = false;
};

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

/**
 * The capabilities of a driver.
 *
 * This represents performance of non-extension operations.
 *
 * Performance of an operation other than {@link OperationType::IF} and
 * {@link OperationType::WHILE} comes from the type of its first operand.
 */
struct Capabilities {
    /**
     * Performance information for the reference workload.
     *
     * Used by a driver to report its performance characteristics.
     */
    struct PerformanceInfo {
        /**
         * Ratio of the time taken by the driver to execute the
         * workload compared to the time the CPU would take for the
         * same workload. A lower number is better.
         */
        float execTime = kDefaultExecTime;

        /**
         * Ratio of the energy used by the driver compared to what
         * the CPU would use for doing the same workload. A lower number
         * is better.
         */
        float powerUsage = kDefaultPowerUsage;
    };

    /**
     * Driver performance when operating on a particular data type.
     * In the case of float32 data, this is used when the calculations
     * are not relaxed.
     */
    struct OperandPerformance {
        OperandType type{};
        PerformanceInfo info;
    };

    class OperandPerformanceTable {
       public:
        static Result<OperandPerformanceTable> create(
                std::vector<OperandPerformance> operandPerformances);

        PerformanceInfo lookup(OperandType type) const;
        const std::vector<OperandPerformance>& asVector() const;

       private:
        explicit OperandPerformanceTable(std::vector<OperandPerformance> operandPerformances);
        std::vector<OperandPerformance> mSorted;
    };

    /**
     * Driver performance when operating on float32 data but performing
     * calculations with range and/or precision as low as that of the IEEE
     * 754 16-bit floating-point format.
     */
    PerformanceInfo relaxedFloat32toFloat16PerformanceScalar;
    PerformanceInfo relaxedFloat32toFloat16PerformanceTensor;

    /**
     * Performance by operand type. Must be sorted by OperandType.
     *
     * If a particular {@link OperandType} is not present in operandPerformance,
     * its performance is treated as
     * { .execTime = FLT_MAX, .powerUsage = FLT_MAX }.
     *
     * Performance does not apply to {@link OperandType::SUBGRAPH}, and a driver
     * must not report operand performance for {@link OperandType::SUBGRAPH}.
     */
    OperandPerformanceTable operandPerformance;

    /**
     * Performance of an {@link OperationType::IF} operation is the sum of
     * {@link Capabilities::ifPerformance} and the mean of performance for the
     * two branch subgraphs, where performance for a subgraph is the sum of the
     * performance of all operations within the subgraph.
     */
    PerformanceInfo ifPerformance;

    /**
     * Performance of a {@link OperationType::WHILE} operation is the sum of
     * {@link Capabilities::whilePerformance}, performance for the condition
     * subgraph and performance for the body subgraph, where performance for a
     * subgraph is the sum of the performance of all operations within the
     * subgraph.
     */
    PerformanceInfo whilePerformance;
};

/**
 * Information about an extension.
 */
struct Extension {
    /**
     * Information about an extension operand type.
     */
    struct OperandTypeInformation {
        /**
         * The extension operand type.
         */
        uint16_t type = 0;

        /**
         * Indicates whether the extension operand type represents a tensor or
         * a scalar.
         */
        bool isTensor = false;

        /**
         * The byte size of the operand (if scalar) or of a single element (if
         * tensor).
         */
        uint32_t byteSize = 0;
    };

    /**
     * The extension name.
     *
     * The name must consist of lowercase latin letters, numbers, periods, and
     * underscore signs. The name must contain at least one period.
     *
     * The name must start with the reverse domain name of the vendor.
     *
     * Example: com.google.test_extension
     */
    std::string name;

    /**
     * Information about operand types defined by the extension.
     */
    std::vector<OperandTypeInformation> operandTypes;
};

/**
 * Describes one operation of the model's graph.
 */
struct Operation {
    /**
     * The operation type.
     */
    OperationType type{};

    /**
     * Describes the table that contains the indexes of the inputs of the
     * operation. The offset is the index in the operandIndexes table.
     */
    std::vector<uint32_t> inputs;

    /**
     * Describes the table that contains the indexes of the outputs of the
     * operation. The offset is the index in the operandIndexes table.
     */
    std::vector<uint32_t> outputs;
};

/**
 * Describes the location of a data object.
 */
struct DataLocation {
    /**
     * The address of the memory where the data is found.
     *
     * This field is only active when lifetime is POINTER.
     */
    std::variant<const void*, void*> pointer;

    /**
     * The index of the memory pool where this location is found.
     */
    uint32_t poolIndex = 0;

    /**
     * Offset in bytes from the start of the pool.
     */
    uint32_t offset = 0;

    /**
     * The length of the data in bytes.
     */
    uint32_t length = 0;

    /**
     * The end padding of the specified memory region in bytes.
     */
    uint32_t padding = 0;
};

/**
 * Describes one operand of the model's graph.
 */
struct Operand {
    /**
     * How an operand is used.
     */
    enum class LifeTime {
        /**
         * The operand is internal to the model. It's created by an operation and
         * consumed by other operations. It must be an output operand of
         * exactly one operation.
         */
        TEMPORARY_VARIABLE = 0,

        /**
         * The operand is an input of a subgraph. It must not be an output
         * operand of any operation.
         *
         * An operand can't be both input and output of a subgraph.
         */
        SUBGRAPH_INPUT = 1,

        /**
         * The operand is an output of a subgraph. It must be an output
         * operand of exactly one operation.
         *
         * An operand can't be both input and output of a subgraph.
         */
        SUBGRAPH_OUTPUT = 2,

        /**
         * The operand is a constant found in Model::operandValues. It must
         * not be an output operand of any operation.
         */
        CONSTANT_COPY = 3,

        /**
         * The operand is a constant that was specified via a Memory
         * object. It must not be an output operand of any operation.
         */
        CONSTANT_REFERENCE = 4,

        /**
         * The operand does not have a value. This is valid only for optional
         * arguments of operations.
         */
        NO_VALUE = 5,

        /**
         * The operand is a reference to a subgraph. It must be an input to one
         * or more {@link OperationType::IF} or {@link OperationType::WHILE}
         * operations.
         */
        SUBGRAPH = 6,

        /**
         * This operand is a constant found in a user buffer. It must not be an
         * output operand of any operation.
         */
        POINTER = 7,
    };

    /**
     * No additional parameters.
     */
    using NoParams = std::monostate;

    /**
     * Parameters for TENSOR_QUANT8_SYMM_PER_CHANNEL operand.
     */
    struct SymmPerChannelQuantParams {
        /** Array of scaling values for each channel. Each value must be greater than zero. */
        std::vector<float> scales;
        /** Index of the channel dimension */
        uint32_t channelDim = 0;
    };

    /**
     * Extension operand parameters.
     *
     * The framework treats this as an opaque data blob.
     * The format is up to individual extensions.
     */
    using ExtensionParams = std::vector<uint8_t>;

    /**
     * Additional parameters specific to a particular operand type.
     */
    using ExtraParams = std::variant<NoParams, SymmPerChannelQuantParams, ExtensionParams>;

    /**
     * The data type.
     *
     * Besides the values listed in {@link OperationType}, any value equal or over
     * (1 << kExtensionTypeBits) is possible and should be interpreted
     * as an extension type according to {@link Model::extensionNameToPrefix}.
     */
    OperandType type{};

    /**
     * Dimensions of the operand.
     *
     * For a scalar operand, dimensions.size() must be 0.
     *
     * A tensor operand with all dimensions specified has "fully
     * specified" dimensions. Whenever possible (i.e., whenever the
     * dimensions are known at model construction time), a tensor
     * operand should have (but is not required to have) fully
     * specified dimensions, in order to enable the best possible
     * performance.
     *
     * If a tensor operand's dimensions are not fully specified, the
     * dimensions of the operand are deduced from the operand
     * dimensions and values of the operation for which that operand
     * is an output or from the corresponding {@link OperationType::IF} or
     * {@link OperationType::WHILE} operation input operand dimensions in the
     * case of referenced subgraph input operands.
     *
     * In the following situations, a tensor operand's dimensions must
     * be fully specified:
     *
     *     - The operand has lifetime CONSTANT_COPY, CONSTANT_REFERENCE, or
     *       POINTER.
     *
     *     - The operand has lifetime SUBGRAPH_INPUT and belongs to the main
     *       subgraph. Fully specified dimensions must either be present in the
     *       Operand or they must be provided in the corresponding
     *       RequestArgument.
     *       EXCEPTION: If the input is optional and omitted
     *       (by setting the hasNoValue field of the corresponding
     *       RequestArgument to true) then it need not have fully
     *       specified dimensions.
     *
     * A tensor operand with some number of unspecified dimensions is
     * represented by setting each unspecified dimension to 0.
     *
     * A tensor operand with unspecified rank is represented by providing
     * an empty dimensions vector.
     */
    Dimensions dimensions;

    /**
     * Quantized scale of the operand.
     *
     * Must be 0 when not applicable to an operand type.
     *
     * See {@link OperandType}.
     */
    float scale = 0.0f;

    /**
     * Quantized zero-point offset of the operand.
     *
     * Must be 0 when not applicable to an operand type.
     *
     * See {@link OperandType}.
     */
    int32_t zeroPoint = 0;

    /**
     * How the operand is used.
     */
    LifeTime lifetime{};

    /**
     * Where to find the data for this operand.
     * If the lifetime is TEMPORARY_VARIABLE, SUBGRAPH_INPUT, SUBGRAPH_OUTPUT,
     * or NO_VALUE:
     * - All the fields must be 0.
     * If the lifetime is CONSTANT_COPY:
     * - location.pointer is null.
     * - location.poolIndex is 0.
     * - location.offset is the offset in bytes into Model::operandValues.
     * - location.length is set.
     * - location.padding is 0.
     * If the lifetime is CONSTANT_REFERENCE:
     * - location.pointer is null.
     * - location.poolIndex is set.
     * - location.offset is the offset in bytes into the specified pool.
     * - location.length is set.
     * - location.padding is set.
     * If the lifetime is SUBGRAPH:
     * - location.pointer is null.
     * - location.poolIndex is 0.
     * - location.offset is the index of the referenced subgraph in
     *   {@link Model::referenced}.
     * - location.length is 0.
     * - location.padding is 0.
     * If the lifetime is POINTER:
     * - location.pointer is non-null.
     * - location.poolIndex is 0.
     * - location.offset is 0.
     * - location.length is set.
     * - location.padding is 0.
     */
    DataLocation location;

    /**
     * Additional parameters specific to a particular operand type.
     */
    ExtraParams extraParams;
};

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

/**
 * The mapping between extension names and prefixes of values like operand and operation type, and
 * token in {@link TokenValuePair}.
 *
 * An operand or operation whose numeric type value is above {@link IDevice::OPERAND_TYPE_BASE_MAX}
 * or {@link IDevice::OPERATION_TYPE_BASE_MAX} respectively should be interpreted as an extension
 * operand/operation. The low kExtensionTypeBits bits of the value correspond to the type ID within
 * the extension and the high kExtensionPrefixBits bits encode the "prefix", which maps uniquely to
 * the extension name. The sign bit is always 0.
 *
 * For example, if a model contains an operation whose value is 0x7AAABBBB and
 * Model::extensionNameToPrefix contains an entry with prefix=0x7AAA and
 * name="vendor.test.test_extension", then the operation should be interpreted as the operation
 * 0xBBBB of the extension named vendor.test.test_extension.
 *
 * This is a one-to-one correspondence. That is, there must be at most one prefix corresponding to
 * each extension name and at most one extension name corresponding to each prefix.
 */
struct ExtensionNameAndPrefix {
    /**
     * The extension name.
     *
     * See {@link Extension::name} for the format specification.
     */
    std::string name;

    /**
     * The extension prefix. Only the lowest 15 bits are used, so the value must be less than 32768.
     */
    uint16_t prefix = 0;
};

/**
 * A Neural Network Model.
 *
 * This includes not only the execution graph, but also constant data such as
 * weights or scalars added at construction time. The only information that
 * may not be known is the shape of the input tensors.
 */
struct Model {
    /**
     * An excerpt of the execution graph.
     */
    struct Subgraph {
        /**
         * All operands included in the subgraph.
         */
        std::vector<Operand> operands;

        /**
         * All operations included in the subgraph.
         *
         * The operations are sorted into execution order. Every operand
         * with lifetime SUBGRAPH_OUTPUT or TEMPORARY_VARIABLE must be
         * written before it is read.
         */
        std::vector<Operation> operations;

        /**
         * Input indexes of the subgraph. There must be at least one.
         *
         * Each value corresponds to the index of the operand in "operands".
         */
        std::vector<uint32_t> inputIndexes;

        /**
         * Output indexes of the subgraph. There must be at least one.
         *
         * Each value corresponds to the index of the operand in "operands".
         */
        std::vector<uint32_t> outputIndexes;
    };

    class OperandValues {
       public:
        OperandValues();
        OperandValues(const uint8_t* data, size_t length);

        // Append a segment of memory (starting at `data` with `length` number of bytes) to the back
        // of `OperandValues`, adding padding as necessary so that the appended data is aligned.
        // Refer to `getAlignmentForLength` for more information on alignment (such as what the
        // current alignments are for different data lengths).
        DataLocation append(const uint8_t* data, size_t length);

        const uint8_t* data() const;
        size_t size() const;

       private:
        std::vector<uint8_t> mData;
    };

    /**
     * The top-level subgraph.
     */
    Subgraph main;

    /**
     * Referenced subgraphs.
     *
     * Each subgraph is referenced by the main subgraph or at least one other
     * referenced subgraph.
     *
     * There must be no reference cycles.
     */
    std::vector<Subgraph> referenced;

    /**
     * A byte buffer containing operand data that were copied into the model.
     *
     * An operand's value must be located here if and only if Operand::lifetime
     * equals Operand::LifeTime::CONSTANT_COPY.
     */
    OperandValues operandValues;

    /**
     * A collection of shared memory pools containing operand values.
     *
     * An operand's value must be located here if and only if Operand::lifetime
     * equals Operand::LifeTime::CONSTANT_REFERENCE.
     */
    std::vector<SharedMemory> pools;

    /**
     * 'true' indicates TENSOR_FLOAT32 may be calculated with range and/or
     * precision as low as that of the IEEE 754 16-bit floating-point format.
     * 'false' indicates TENSOR_FLOAT32 must be calculated using at least the
     * range and precision of the IEEE 754 32-bit floating-point format.
     */
    bool relaxComputationFloat32toFloat16 = false;

    /**
     * The mapping between extension names and prefixes of operand and
     * operation type values.
     *
     * An operand or operation whose numeric type value is equal to or greater
     * than (1 << kExtensionTypeBits) should be interpreted
     * as an extension operand. The low
     * {@link kExtensionTypeBits} bits of the value correspond to the type ID
     * within the extension and the high {@link kExtensionPrefixBits} bits encode
     * the "prefix", which maps uniquely to the extension name.
     *
     * For example, if a model contains an operation whose value is
     * 0xAAAABBBB and extensionNameToPrefix contains an entry with
     * prefix=0xAAAA and name="vendor.test.test_extension", then
     * the operation should be interpreted as the operation 0xBBBB
     * of the extension named vendor.test.test_extension.
     *
     * This is a one-to-one correspondence. That is, there must be at most one
     * prefix corresponding to each extension name and at most one extension
     * name corresponding to each prefix.
     */
    std::vector<ExtensionNameAndPrefix> extensionNameToPrefix;
};

/**
 * A buffer descriptor. Describes the properties of a buffer.
 */
struct BufferDesc {
    /**
     * Dimensions of the buffer. May have unknown dimensions or rank. A buffer with some number
     * of unspecified dimensions is represented by setting each unspecified dimension to 0. A
     * buffer with unspecified rank is represented by providing an empty dimensions vector.
     */
    Dimensions dimensions;
};

/**
 * Describes a role of an input or output to a prepared model.
 */
struct BufferRole {
    /**
     * The index of the IPreparedModel within the "preparedModel" argument passed in
     * IDevice::allocate.
     */
    uint32_t modelIndex = 0;

    /**
     * The index of the input or output operand.
     */
    uint32_t ioIndex = 0;

    /**
     * A floating-point value within the range (0.0, 1.0]. Describes how likely the
     * buffer is to be used in the specified role. This is provided as a hint to
     * optimize the case when multiple roles prefer different buffer locations or data
     * layouts.
     */
    float probability = 0.0f;
};

/**
 * Inputs to be sent to and outputs to be retrieved from a prepared model.
 *
 * A Request serves two primary tasks:
 * 1) Provides the input and output data to be used when executing the model.
 * 2) Specifies any updates to the input operand metadata that were left
 *    unspecified at model preparation time.
 *
 * An output must not overlap with any other output, with an input, or
 * with an operand of lifetime CONSTANT_REFERENCE.
 */
struct Request {
    /**
     * Metadata information specifying the location of the input or output data and
     * any updates to the input or output operand.
     */
    struct Argument {
        enum class LifeTime {
            POOL = 0,
            NO_VALUE = 1,
            POINTER = 2,
        };

        LifeTime lifetime{};

        /**
         * The location within one of the memory pools passed in the Request.
         */
        DataLocation location;

        /**
         * Updated dimension information.
         *
         * If dimensions.size() > 0, dimension information was provided
         * along with the argument. This can be the case for models that
         * accept inputs of varying size. This can't change the rank, just
         * the value of the dimensions that were unspecified in the
         * model. If dimensions.size() > 0, then all dimensions must be
         * specified here; and any dimension that was specified in the
         * model must have the same value here.
         *
         * If the dimensions in the model are not fully specified, then
         * they must be fully specified here, unless hasNoValue is set to
         * true. If the dimensions in the model are fully specified, then
         * either dimensions.size() may be 0, or the dimensions in the
         * model must be identical to the dimensions here.
         */
        Dimensions dimensions;
    };

    /**
     * Specifies a driver-managed buffer. It is the token corresponding to an
     * IBuffer returned from IDevice::allocate, and is specific to the IDevice
     * object.
     */
    enum class MemoryDomainToken : uint32_t {};

    /**
     * A memory pool.
     */
    using MemoryPool = std::variant<SharedMemory, MemoryDomainToken, SharedBuffer>;

    /**
     * Input data and information to be used in the execution of a prepared
     * model.
     *
     * The index of the input corresponds to the index in Model::main::inputIndexes.
     *   E.g., inputs[i] corresponds to Model::main::inputIndexes[i].
     */
    std::vector<Argument> inputs;

    /**
     * Output data and information to be used in the execution of a prepared
     * model.
     *
     * The index of the output corresponds to the index in Model::main::outputIndexes.
     *   E.g., outputs[i] corresponds to Model::main::outputIndexes[i].
     */
    std::vector<Argument> outputs;

    /**
     * A collection of memory pools containing operand data for both the
     * inputs and the outputs to a model.
     */
    std::vector<MemoryPool> pools;
};

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

/**
 * Timing information measured during execution. Each time is a duration from
 * the beginning of some task to the end of that task, including time when that
 * task is not active (for example, preempted by some other task, or
 * waiting for some resource to become available).
 *
 * Times are measured in nanoseconds.
 */
struct Timing {
    /** Execution time on device (not driver, which runs on host processor). */
    OptionalDuration timeOnDevice;
    /** Execution time in driver (including time on device). */
    OptionalDuration timeInDriver;
};

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
