#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_TYPES_H

#include <android/hardware/media/bufferpool/2.0/types.h>
#include <android/hidl/safe_union/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace media {
namespace c2 {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : int32_t;
struct FieldId;
struct ParamField;
struct ParamDescriptor;
struct ValueRange;
struct FieldSupportedValues;
struct ParamFieldValues;
struct FieldDescriptor;
struct StructDescriptor;
struct SettingResult;
struct WorkOrdinal;
struct BaseBlock;
struct Block;
struct Buffer;
struct InfoBuffer;
struct FrameData;
struct Worklet;
struct Work;
struct WorkBundle;
struct FieldSupportedValuesQuery;
struct FieldSupportedValuesQueryResult;

/**
 * Common return values for Codec2 operations.
 */
enum class Status : int32_t {
    /**
     * Operation completed successfully.
     */
    OK = 0,
    /**
     * Argument has invalid value (user error).
     */
    BAD_VALUE = -22 /* -22 */,
    /**
     * Argument uses invalid index (user error).
     */
    BAD_INDEX = -75 /* -75 */,
    /**
     * Argument/Index is valid but not possible.
     */
    CANNOT_DO = -2147483646 /* -2147483646 */,
    /**
     * Object already exists.
     */
    DUPLICATE = -17 /* -17 */,
    /**
     * Object not found.
     */
    NOT_FOUND = -2 /* -2 */,
    /**
     * Operation is not permitted in the current state.
     */
    BAD_STATE = -38 /* -38 */,
    /**
     * Operation would block but blocking is not permitted.
     */
    BLOCKING = -9930 /* -9930 */,
    /**
     * Not enough memory to complete operation.
     */
    NO_MEMORY = -12 /* -12 */,
    /**
     * Missing permission to complete operation.
     */
    REFUSED = -1 /* -1 */,
    /**
     * Operation did not complete within timeout.
     */
    TIMED_OUT = -110 /* -110 */,
    /**
     * Operation is not implemented/supported (optional only).
     */
    OMITTED = -74 /* -74 */,
    /**
     * Some unexpected error prevented the operation.
     */
    CORRUPTED = -2147483648 /* -2147483648 */,
    /**
     * Status has not been initialized.
     */
    NO_INIT = -19 /* -19 */,
};

/**
 * C2Param structure index.
 *
 * This is a number that is unique for each C2Param structure type.
 *
 * @sa Codec 2.0 standard.
 */
typedef uint32_t ParamIndex;

/**
 * Flattened representation of C2Param objects.
 *
 * The `Params` type is an array of bytes made up by concatenating a list of
 * C2Param objects. The start index (offset into @ref Params) of each C2Param
 * object in the list is divisible by 8. Up to 7 padding bytes may be added
 * after each C2Param object to achieve this 64-bit alignment.
 *
 * Each C2Param object has the following layout:
 * - 4 bytes: C2Param structure index (of type @ref ParamIndex) identifying the
 *   type of the C2Param object.
 * - 4 bytes: size of the C2Param object (unsigned 4-byte integer).
 * - (size - 8) bytes: data of the C2Param object.
 *
 * In order to interpret each C2Param object correctly, its structure must be
 * described by IComponentStore::getStructDescriptors().
 *
 * @note Please refer to the Codec 2.0 standard for the list of standard
 * parameter structures.
 *
 * @sa Codec 2.0 standard.
 */
typedef ::android::hardware::hidl_vec<uint8_t> Params;

/**
 * Identifying information of a field relative to a known C2Param structure.
 *
 * Within a given C2Param structure, each field is uniquely identified by @ref
 * FieldId.
 */
struct FieldId final {
    /**
     * Offset of the field in bytes.
     */
    uint32_t offset __attribute__ ((aligned(4)));
    /**
     * Size of the field in bytes.
     */
    uint32_t size __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldId, offset) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldId, size) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::FieldId) == 8, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::FieldId) == 4, "wrong alignment");

/**
 * Reference to a field in a C2Param structure.
 */
struct ParamField final {
    /**
     * Index of the C2Param structure.
     */
    uint32_t index __attribute__ ((aligned(4)));
    /**
     * Identifier of the field inside the C2Param structure.
     */
    ::android::hardware::media::c2::V1_0::FieldId fieldId __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::ParamField, index) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::ParamField, fieldId) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::ParamField) == 12, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::ParamField) == 4, "wrong alignment");

/**
 * Usage description of a C2Param structure.
 *
 * @ref ParamDescriptor is returned by IConfigurable::querySupportedParams().
 */
struct ParamDescriptor final {
    // Forward declaration for forward reference support:
    enum class Attrib : uint32_t;

    enum class Attrib : uint32_t {
        /**
         * The parameter is required to be specified.
         */
        REQUIRED = 1u /* 1u << 0 */,
        /**
         * The parameter retains its value.
         */
        PERSISTENT = 2u /* 1u << 1 */,
        /**
         * The parameter is strict.
         */
        STRICT = 4u /* 1u << 2 */,
        /**
         * The parameter is publicly read-only.
         */
        READ_ONLY = 8u /* 1u << 3 */,
        /**
         * The parameter must not be visible to clients.
         */
        HIDDEN = 16u /* 1u << 4 */,
        /**
         * The parameter must not be used by framework (other than testing).
         */
        INTERNAL = 32u /* 1u << 5 */,
        /**
         * The parameter is publicly constant (hence read-only).
         */
        CONST = 64u /* 1u << 6 */,
    };

    /**
     * Index of the C2Param structure being described.
     */
    uint32_t index __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib> attrib __attribute__ ((aligned(4)));
    /**
     * Name of the structure. This must be unique for each structure.
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * Indices of other C2Param structures that this C2Param structure depends
     * on.
     */
    ::android::hardware::hidl_vec<uint32_t> dependencies __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::ParamDescriptor, index) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::ParamDescriptor, attrib) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::ParamDescriptor, name) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::ParamDescriptor, dependencies) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::ParamDescriptor) == 40, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::ParamDescriptor) == 8, "wrong alignment");

/**
 * An untyped value that can fit in 64 bits, the type of which is communicated
 * via a separate channel (@ref FieldSupportedValues.type).
 */
typedef uint64_t PrimitiveValue;

/**
 * Description of a set of values.
 *
 * If the `step` member is 0, and `num` and `denom` are both 1, the `Range`
 * structure represents a closed interval bounded by `min` and `max`.
 *
 * Otherwise, the #ValueRange structure represents a finite sequence of numbers
 * produced from the following recurrence relation:
 *
 * @code
 * v[0] = min
 * v[i] = v[i - 1] * num / denom + step ; i >= 1
 * @endcode
 *
 * Both the ratio `num / denom` and the value `step` must be positive. The
 * last number in the sequence described by this #Range structure is the
 * largest number in the sequence that is smaller than or equal to `max`.
 *
 * @note
 * The division in the formula may truncate the result if the data type of
 * these values is an integral type.
 */
struct ValueRange final {
    /**
     * Lower end of the range (inclusive).
     */
    uint64_t min __attribute__ ((aligned(8)));
    /**
     * Upper end of the range (inclusive).
     */
    uint64_t max __attribute__ ((aligned(8)));
    /**
     * The non-homogeneous term in the recurrence relation.
     */
    uint64_t step __attribute__ ((aligned(8)));
    /**
     * The numerator of the scale coefficient in the recurrence relation.
     */
    uint64_t num __attribute__ ((aligned(8)));
    /**
     * The denominator of the scale coefficient in the recurrence relation.
     */
    uint64_t denom __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::ValueRange, min) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::ValueRange, max) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::ValueRange, step) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::ValueRange, num) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::ValueRange, denom) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::ValueRange) == 40, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::ValueRange) == 8, "wrong alignment");

/*
 * Description of supported values for a field.
 *
 * This can be a continuous range or a discrete set of values.
 *
 * The intended type of values must be made clear in the context where
 * `FieldSupportedValues` is used.
 */
struct FieldSupportedValues final {
    enum class hidl_discriminator : uint8_t {
        /**
         * No supported values
         */
        empty = 0,  // ::android::hidl::safe_union::V1_0::Monostate
        /**
         * Numeric range, described in a #ValueRange structure
         */
        range = 1,  // ::android::hardware::media::c2::V1_0::ValueRange
        /**
         * List of values
         */
        values = 2,  // ::android::hardware::hidl_vec<uint64_t>
        /**
         * List of flags that can be OR-ed
         */
        flags = 3,  // ::android::hardware::hidl_vec<uint64_t>
    };

    FieldSupportedValues();
    ~FieldSupportedValues();
    FieldSupportedValues(FieldSupportedValues&&);
    FieldSupportedValues(const FieldSupportedValues&);
    FieldSupportedValues& operator=(FieldSupportedValues&&);
    FieldSupportedValues& operator=(const FieldSupportedValues&);

    void empty(const ::android::hidl::safe_union::V1_0::Monostate&);
    void empty(::android::hidl::safe_union::V1_0::Monostate&&);
    ::android::hidl::safe_union::V1_0::Monostate& empty();
    const ::android::hidl::safe_union::V1_0::Monostate& empty() const;

    void range(const ::android::hardware::media::c2::V1_0::ValueRange&);
    void range(::android::hardware::media::c2::V1_0::ValueRange&&);
    ::android::hardware::media::c2::V1_0::ValueRange& range();
    const ::android::hardware::media::c2::V1_0::ValueRange& range() const;

    void values(const ::android::hardware::hidl_vec<uint64_t>&);
    void values(::android::hardware::hidl_vec<uint64_t>&&);
    ::android::hardware::hidl_vec<uint64_t>& values();
    const ::android::hardware::hidl_vec<uint64_t>& values() const;

    void flags(const ::android::hardware::hidl_vec<uint64_t>&);
    void flags(::android::hardware::hidl_vec<uint64_t>&&);
    ::android::hardware::hidl_vec<uint64_t>& flags();
    const ::android::hardware::hidl_vec<uint64_t>& flags() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::media::c2::V1_0::FieldSupportedValues, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hidl::safe_union::V1_0::Monostate empty __attribute__ ((aligned(1)));
        ::android::hardware::media::c2::V1_0::ValueRange range __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint64_t> values __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint64_t> flags __attribute__ ((aligned(8)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_union) == 40, "wrong size");
    static_assert(__alignof(::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_union) == 8, "wrong alignment");
    static_assert(sizeof(::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::media::c2::V1_0::FieldSupportedValues) == 48, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::FieldSupportedValues) == 8, "wrong alignment");

/**
 * Supported values for a field.
 *
 * This is a pair of the field specifier together with an optional supported
 * values object. This structure is used when reporting parameter configuration
 * failures and conflicts.
 */
struct ParamFieldValues final {
    /**
     * Reference to a field or a C2Param structure.
     */
    ::android::hardware::media::c2::V1_0::ParamField paramOrField __attribute__ ((aligned(4)));
    /**
     * Optional supported values for the field if #paramOrField specifies an
     * actual field that is numeric (non struct, blob or string). Supported
     * values for arrays (including string and blobs) describe the supported
     * values for each element (character for string, and bytes for blobs). It
     * is optional for read-only strings and blobs.
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::FieldSupportedValues> values __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::ParamFieldValues, paramOrField) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::ParamFieldValues, values) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::ParamFieldValues) == 32, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::ParamFieldValues) == 8, "wrong alignment");

/**
 * Description of a field inside a C2Param structure.
 */
struct FieldDescriptor final {
    // Forward declaration for forward reference support:
    enum class Type : uint32_t;
    struct NamedValue;

    /**
     * Possible types of the field.
     */
    enum class Type : uint32_t {
        NO_INIT = 0u,
        INT32 = 1u /* ::android::hardware::media::c2::V1_0::FieldDescriptor::Type.NO_INIT implicitly + 1 */,
        UINT32 = 2u /* ::android::hardware::media::c2::V1_0::FieldDescriptor::Type.INT32 implicitly + 1 */,
        CNTR32 = 3u /* ::android::hardware::media::c2::V1_0::FieldDescriptor::Type.UINT32 implicitly + 1 */,
        INT64 = 4u /* ::android::hardware::media::c2::V1_0::FieldDescriptor::Type.CNTR32 implicitly + 1 */,
        UINT64 = 5u /* ::android::hardware::media::c2::V1_0::FieldDescriptor::Type.INT64 implicitly + 1 */,
        CNTR64 = 6u /* ::android::hardware::media::c2::V1_0::FieldDescriptor::Type.UINT64 implicitly + 1 */,
        FLOAT = 7u /* ::android::hardware::media::c2::V1_0::FieldDescriptor::Type.CNTR64 implicitly + 1 */,
        /**
         * Fixed-size string (POD).
         */
        STRING = 256u /* 0x100 */,
        /**
         * A blob has no sub-elements and can be thought of as an array of
         * bytes. However, bytes cannot be individually addressed by clients.
         */
        BLOB = 257u /* ::android::hardware::media::c2::V1_0::FieldDescriptor::Type.STRING implicitly + 1 */,
        /**
         * The field is a structure that may contain other fields.
         */
        STRUCT = 131072u /* 0x20000 */,
    };

    /**
     * Named value type. This is used for defining an enum value for a numeric
     * type.
     */
    struct NamedValue final {
        /**
         * Name of the enum value. This must be unique for each enum value in
         * the same field.
         */
        ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
        /**
         * Underlying value of the enum value. Multiple enum names may have the
         * same underlying value.
         */
        uint64_t value __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue, name) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue, value) == 16, "wrong offset");
    static_assert(sizeof(::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue) == 8, "wrong alignment");

    /**
     * Location of the field in the C2Param structure
     */
    ::android::hardware::media::c2::V1_0::FieldId fieldId __attribute__ ((aligned(4)));
    /**
     * Type of the field.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::FieldDescriptor::Type> type __attribute__ ((aligned(4)));
    /**
     * If #type is #Type.STRUCT, #structIndex is the C2Param structure index;
     * otherwise, #structIndex is not used.
     */
    uint32_t structIndex __attribute__ ((aligned(4)));
    /**
     * Extent of the field.
     * - For a non-array field, #extent is 1.
     * - For a fixed-length array field, #extent is the length. An array field
     *   of length 1 is indistinguishable from a non-array field.
     * - For a variable-length array field, #extent is 0. This can only occur as
     *   the last member of a C2Param structure.
     */
    uint32_t extent __attribute__ ((aligned(4)));
    /**
     * Name of the field. This must be unique for each field in the same
     * structure.
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * List of enum values. This is not used when #type is not one of the
     * numeric types.
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue> namedValues __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldDescriptor, fieldId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldDescriptor, type) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldDescriptor, structIndex) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldDescriptor, extent) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldDescriptor, name) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldDescriptor, namedValues) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::FieldDescriptor) == 56, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::FieldDescriptor) == 8, "wrong alignment");

/**
 * Description of a C2Param structure. It consists of an index and a list of
 * `FieldDescriptor`s.
 */
struct StructDescriptor final {
    /**
     * Index of the structure.
     */
    uint32_t type __attribute__ ((aligned(4)));
    /**
     * List of fields in the structure.
     *
     * Fields are ordered by their offsets. A field that is a structure is
     * ordered before its members.
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::FieldDescriptor> fields __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::StructDescriptor, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::StructDescriptor, fields) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::StructDescriptor) == 24, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::StructDescriptor) == 8, "wrong alignment");

/**
 * Information describing the reason the parameter settings may fail, or may be
 * overridden.
 */
struct SettingResult final {
    // Forward declaration for forward reference support:
    enum class Failure : uint32_t;

    /**
     * Failure code
     */
    enum class Failure : uint32_t {
        /**
         * Parameter is not supported.
         */
        BAD_TYPE = 0u,
        /**
         * Parameter is not supported on the specific port.
         */
        BAD_PORT = 1u /* ::android::hardware::media::c2::V1_0::SettingResult::Failure.BAD_TYPE implicitly + 1 */,
        /**
         * Parameter is not supported on the specific stream.
         */
        BAD_INDEX = 2u /* ::android::hardware::media::c2::V1_0::SettingResult::Failure.BAD_PORT implicitly + 1 */,
        /**
         * Parameter is read-only and cannot be set.
         */
        READ_ONLY = 3u /* ::android::hardware::media::c2::V1_0::SettingResult::Failure.BAD_INDEX implicitly + 1 */,
        /**
         * Parameter mismatches input data.
         */
        MISMATCH = 4u /* ::android::hardware::media::c2::V1_0::SettingResult::Failure.READ_ONLY implicitly + 1 */,
        /**
         * Strict parameter does not accept value for the field at all.
         */
        BAD_VALUE = 5u /* ::android::hardware::media::c2::V1_0::SettingResult::Failure.MISMATCH implicitly + 1 */,
        /**
         * Strict parameter field value is in conflict with an/other
         * setting(s).
         */
        CONFLICT = 6u /* ::android::hardware::media::c2::V1_0::SettingResult::Failure.BAD_VALUE implicitly + 1 */,
        /**
         * Parameter field is out of range due to other settings. (This failure
         * mode can only be used for strict calculated parameters.)
         */
        UNSUPPORTED = 7u /* ::android::hardware::media::c2::V1_0::SettingResult::Failure.CONFLICT implicitly + 1 */,
        /**
         * Field does not access the requested parameter value at all. It has
         * been corrected to the closest supported value. This failure mode is
         * provided to give guidance as to what are the currently supported
         * values for this field (which may be a subset of the at-all-potential
         * values).
         */
        INFO_BAD_VALUE = 8u /* ::android::hardware::media::c2::V1_0::SettingResult::Failure.UNSUPPORTED implicitly + 1 */,
        /**
         * Requested parameter value is in conflict with an/other setting(s)
         * and has been corrected to the closest supported value. This failure
         * mode is given to provide guidance as to what are the currently
         * supported values as well as to optionally provide suggestion to the
         * client as to how to enable the requested parameter value.
         */
        INFO_CONFLICT = 9u /* ::android::hardware::media::c2::V1_0::SettingResult::Failure.INFO_BAD_VALUE implicitly + 1 */,
    };

    ::android::hardware::media::c2::V1_0::SettingResult::Failure failure __attribute__ ((aligned(4)));
    /**
     * Failing (or corrected) field or parameter and optionally, currently
     * supported values for the field. Values must only be set for field
     * failures other than `BAD_VALUE`, and only if they are different from the
     * globally supported values (e.g. due to restrictions by another parameter
     * or input data).
     */
    ::android::hardware::media::c2::V1_0::ParamFieldValues field __attribute__ ((aligned(8)));
    /**
     * Conflicting parameters or fields with (optional) suggested values for any
     * conflicting fields to avoid the conflict. Values must only be set for
     * `CONFLICT`, `UNSUPPORTED` or `INFO_CONFLICT` failure code.
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::ParamFieldValues> conflicts __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::SettingResult, failure) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::SettingResult, field) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::SettingResult, conflicts) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::SettingResult) == 56, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::SettingResult) == 8, "wrong alignment");

/**
 * Ordering information of @ref FrameData objects. Each member is used for
 * comparing urgency: a smaller difference from a reference value indicates that
 * the associated Work object is more urgent. The reference value for each
 * member is initialized the first time it is communicated between the client
 * and the codec, and it may be updated to later values that are communicated.
 *
 * Each member of `WorkOrdinal` is stored as an unsigned integer, but the actual
 * order it represents is derived by subtracting the reference value, then
 * interpreting the result as a signed number with the same storage size (using
 * two's complement).
 *
 * @note `WorkOrdinal` is the HIDL counterpart of `C2WorkOrdinalStruct` in the
 * Codec 2.0 standard.
 */
struct WorkOrdinal final {
    /**
     * Timestamp in microseconds.
     */
    uint64_t timestampUs __attribute__ ((aligned(8)));
    /**
     * Frame index.
     */
    uint64_t frameIndex __attribute__ ((aligned(8)));
    /**
     * Component specific frame ordinal.
     */
    uint64_t customOrdinal __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::WorkOrdinal, timestampUs) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::WorkOrdinal, frameIndex) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::WorkOrdinal, customOrdinal) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::WorkOrdinal) == 24, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::WorkOrdinal) == 8, "wrong alignment");

/**
 * Storage type for `BaseBlock`.
 *
 * A `BaseBlock` is a representation of a codec memory block. Coded data,
 * decoded data, codec-specific data, and other codec-related data are all sent
 * in the form of BaseBlocks.
 */
struct BaseBlock final {
    enum class hidl_discriminator : uint8_t {
        /**
         * #nativeBlock is the opaque representation of a buffer.
         */
        nativeBlock = 0,  // ::android::hardware::hidl_handle
        /**
         * #pooledBlock is a reference to a buffer handled by a BufferPool.
         */
        pooledBlock = 1,  // ::android::hardware::media::bufferpool::V2_0::BufferStatusMessage
    };

    BaseBlock();
    ~BaseBlock();
    BaseBlock(BaseBlock&&);
    BaseBlock(const BaseBlock&);
    BaseBlock& operator=(BaseBlock&&);
    BaseBlock& operator=(const BaseBlock&);

    void nativeBlock(const ::android::hardware::hidl_handle&);
    void nativeBlock(::android::hardware::hidl_handle&&);
    ::android::hardware::hidl_handle& nativeBlock();
    const ::android::hardware::hidl_handle& nativeBlock() const;

    void pooledBlock(const ::android::hardware::media::bufferpool::V2_0::BufferStatusMessage&);
    void pooledBlock(::android::hardware::media::bufferpool::V2_0::BufferStatusMessage&&);
    ::android::hardware::media::bufferpool::V2_0::BufferStatusMessage& pooledBlock();
    const ::android::hardware::media::bufferpool::V2_0::BufferStatusMessage& pooledBlock() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::media::c2::V1_0::BaseBlock, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hardware::hidl_handle nativeBlock __attribute__ ((aligned(8)));
        ::android::hardware::media::bufferpool::V2_0::BufferStatusMessage pooledBlock __attribute__ ((aligned(8)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::media::c2::V1_0::BaseBlock::hidl_union) == 40, "wrong size");
    static_assert(__alignof(::android::hardware::media::c2::V1_0::BaseBlock::hidl_union) == 8, "wrong alignment");
    static_assert(sizeof(::android::hardware::media::c2::V1_0::BaseBlock::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::media::c2::V1_0::BaseBlock::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::media::c2::V1_0::BaseBlock) == 48, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::BaseBlock) == 8, "wrong alignment");

/**
 * Reference to a @ref BaseBlock within a @ref WorkBundle.
 *
 * `Block` contains additional attributes that `BaseBlock` does not. These
 * attributes may differ among `Block` objects that refer to the same
 * `BaseBlock` in the same `WorkBundle`.
 */
struct Block final {
    /**
     * Identity of a `BaseBlock` within a `WorkBundle`. This is an index into
     * #WorkBundle.baseBlocks.
     */
    uint32_t index __attribute__ ((aligned(4)));
    /**
     * Metadata associated with this `Block`.
     */
    ::android::hardware::hidl_vec<uint8_t> meta __attribute__ ((aligned(8)));
    /**
     * Fence for synchronizing `Block` access.
     */
    ::android::hardware::hidl_handle fence __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::Block, index) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::Block, meta) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::Block, fence) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::Block) == 40, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::Block) == 8, "wrong alignment");

/**
 * A codec buffer, which is a collection of @ref Block objects and metadata.
 *
 * This is a part of @ref FrameData.
 */
struct Buffer final {
    /**
     * Metadata associated with the buffer.
     */
    ::android::hardware::hidl_vec<uint8_t> info __attribute__ ((aligned(8)));
    /**
     * Blocks contained in the buffer.
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::Block> blocks __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::Buffer, info) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::Buffer, blocks) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::Buffer) == 32, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::Buffer) == 8, "wrong alignment");

/**
 * An extension of @ref Buffer that also contains a C2Param structure index.
 *
 * This is a part of @ref FrameData.
 */
struct InfoBuffer final {
    /**
     * A C2Param structure index.
     */
    uint32_t index __attribute__ ((aligned(4)));
    /**
     * Associated @ref Buffer object.
     */
    ::android::hardware::media::c2::V1_0::Buffer buffer __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::InfoBuffer, index) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::InfoBuffer, buffer) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::InfoBuffer) == 40, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::InfoBuffer) == 8, "wrong alignment");

/**
 * Data for an input frame or an output frame.
 *
 * This structure represents a @e frame with its metadata. A @e frame consists
 * of an ordered set of buffers, configuration changes, and info buffers along
 * with some non-configuration metadata.
 *
 * @note `FrameData` is the HIDL counterpart of `C2FrameData` in the Codec 2.0
 * standard.
 */
struct FrameData final {
    // Forward declaration for forward reference support:
    enum class Flags : uint32_t;

    enum class Flags : uint32_t {
        /**
         * For input frames: no output frame shall be generated when processing
         * this frame, but metadata must still be processed.
         *
         * For output frames: this frame must be discarded but metadata is still
         * valid.
         */
        DROP_FRAME = 1u /* (1 << 0) */,
        /**
         * This frame is the last frame of the current stream. Further frames
         * are part of a new stream.
         */
        END_OF_STREAM = 2u /* (1 << 1) */,
        /**
         * This frame must be discarded with its metadata.
         *
         * This flag is only set by components, e.g. as a response to the flush
         * command.
         */
        DISCARD_FRAME = 4u /* (1 << 2) */,
        /**
         * This frame is not the last frame produced for the input.
         *
         * This flag is normally set by the component - e.g. when an input frame
         * results in multiple output frames, this flag is set on all but the
         * last output frame.
         *
         * Also, when components are chained, this flag should be propagated
         * down the work chain. That is, if set on an earlier frame of a
         * work-chain, it should be propagated to all later frames in that
         * chain. Additionally, components down the chain could set this flag
         * even if not set earlier, e.g. if multiple output frames are generated
         * at that component for the input frame.
         */
        FLAG_INCOMPLETE = 8u /* (1 << 3) */,
        /**
         * This frame contains only codec-specific configuration data, and no
         * actual access unit.
         *
         * @deprecated Pass codec configuration with the codec-specific
         * configuration info together with the access unit.
         */
        CODEC_CONFIG = 2147483648u /* (1u << 31) */,
    };

    /**
     * Frame flags, as described in #Flags.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::FrameData::Flags> flags __attribute__ ((aligned(4)));
    /**
     * @ref WorkOrdinal of the frame.
     */
    ::android::hardware::media::c2::V1_0::WorkOrdinal ordinal __attribute__ ((aligned(8)));
    /**
     * List of frame buffers.
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::Buffer> buffers __attribute__ ((aligned(8)));
    /**
     * List of configuration updates.
     */
    ::android::hardware::hidl_vec<uint8_t> configUpdate __attribute__ ((aligned(8)));
    /**
     * List of info buffers.
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::InfoBuffer> infoBuffers __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::FrameData, flags) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FrameData, ordinal) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FrameData, buffers) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FrameData, configUpdate) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FrameData, infoBuffers) == 64, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::FrameData) == 80, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::FrameData) == 8, "wrong alignment");

/**
 * In/out structure containing some instructions for and results from output
 * processing.
 *
 * This is a part of @ref Work. One `Worklet` corresponds to one output
 * @ref FrameData. The client must construct an original `Worklet` object inside
 * a @ref Work object for each expected output before calling
 * IComponent::queue().
 */
struct Worklet final {
    /**
     * Component id. (Input)
     *
     * This is used only when tunneling is enabled.
     *
     * When used, this must match the return value from IConfigurable::getId().
     */
    uint32_t componentId __attribute__ ((aligned(4)));
    /**
     * List of C2Param objects describing tunings to be applied before
     * processing this `Worklet`. (Input)
     */
    ::android::hardware::hidl_vec<uint8_t> tunings __attribute__ ((aligned(8)));
    /**
     * List of failures. (Output)
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::SettingResult> failures __attribute__ ((aligned(8)));
    /**
     * Output frame data. (Output)
     */
    ::android::hardware::media::c2::V1_0::FrameData output __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::Worklet, componentId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::Worklet, tunings) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::Worklet, failures) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::Worklet, output) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::Worklet) == 120, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::Worklet) == 8, "wrong alignment");

/**
 * A collection of input data to and output data from the component.
 *
 * A `Work` object holds information about a single work item. It is created by
 * the client and passed to the component via IComponent::queue(). The component
 * has two ways of returning a `Work` object to the client:
 *   1. If the queued `Work` object has been successfully processed,
 *      IComponentListener::onWorkDone() shall be called to notify the listener,
 *      and the output shall be included in the returned `Work` object.
 *   2. If the client calls IComponent::flush(), a `Work` object that has not
 *      been processed shall be returned.
 *
 * `Work` is a part of @ref WorkBundle.
 */
struct Work final {
    /**
     * Additional work chain info not part of this work.
     */
    ::android::hardware::hidl_vec<uint8_t> chainInfo __attribute__ ((aligned(8)));
    /**
     * @ref FrameData for the input.
     */
    ::android::hardware::media::c2::V1_0::FrameData input __attribute__ ((aligned(8)));
    /**
     * The chain of `Worklet`s.
     *
     * The length of #worklets is 1 when tunneling is not enabled.
     *
     * If #worklets has more than a single element, the tunnels between
     * successive components of the work chain must have been successfully
     * pre-registered at the time that the `Work` is submitted. Allocating the
     * output buffers in the `Worklet`s is the responsibility of each component
     * in the chain.
     *
     * Upon `Work` submission, #worklets must be an appropriately sized vector
     * containing `Worklet`s with @ref Worklet.hasOutput set to `false`. After a
     * successful processing, all but the final `Worklet` in the returned
     * #worklets must have @ref Worklet.hasOutput set to `false`.
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::Worklet> worklets __attribute__ ((aligned(8)));
    /**
     * The number of `Worklet`s successfully processed in this chain.
     *
     * This must be initialized to 0 by the client when the `Work` is submitted,
     * and it must contain the number of `Worklet`s that were successfully
     * processed when the `Work` is returned to the client.
     *
     * #workletsProcessed cannot exceed the length of #worklets. If
     * #workletsProcessed is smaller than the length of #worklets, #result
     * cannot be `OK`.
     */
    uint32_t workletsProcessed __attribute__ ((aligned(4)));
    /**
     * The final outcome of the `Work` (corresponding to #workletsProcessed).
     *
     * The value of @ref Status.OK implies that all `Worklet`s have been
     * successfully processed.
     */
    ::android::hardware::media::c2::V1_0::Status result __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::Work, chainInfo) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::Work, input) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::Work, worklets) == 96, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::Work, workletsProcessed) == 112, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::Work, result) == 116, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::Work) == 120, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::Work) == 8, "wrong alignment");

/**
 * List of `Work` objects.
 *
 * `WorkBundle` is used in IComponent::queue(), IComponent::flush() and
 * IComponentListener::onWorkDone(). A `WorkBundle` object consists of a list of
 * `Work` objects and a list of `BaseBlock` objects. Bundling multiple `Work`
 * objects together provides two benefits:
 *   1. Batching of `Work` objects can reduce the number of IPC calls.
 *   2. If multiple `Work` objects contain `Block`s that refer to the same
 *      `BaseBlock`, the number of `BaseBlock`s that is sent between processes
 *      is also reduced.
 *
 * @note `WorkBundle` is the HIDL counterpart of the vector of `C2Work` in the
 * Codec 2.0 standard. The presence of #baseBlocks helps with minimizing the
 * data transferred over an IPC.
 */
struct WorkBundle final {
    /**
     * A list of Work items.
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::Work> works __attribute__ ((aligned(8)));
    /**
     * A list of blocks indexed by elements of #works.
     */
    ::android::hardware::hidl_vec<::android::hardware::media::c2::V1_0::BaseBlock> baseBlocks __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::WorkBundle, works) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::WorkBundle, baseBlocks) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::WorkBundle) == 32, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::WorkBundle) == 8, "wrong alignment");

/**
 * Query information for supported values of a field. This is used as input to
 * IConfigurable::querySupportedValues().
 */
struct FieldSupportedValuesQuery final {
    // Forward declaration for forward reference support:
    enum class Type : uint32_t;

    enum class Type : uint32_t {
        /**
         * Query all possible values regardless of other settings.
         */
        POSSIBLE = 0u,
        /**
         * Query currently possible values given dependent settings.
         */
        CURRENT = 1u /* ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type.POSSIBLE implicitly + 1 */,
    };

    /**
     * Identity of the field to query.
     */
    ::android::hardware::media::c2::V1_0::ParamField field __attribute__ ((aligned(4)));
    /**
     * Type of the query. See #Type for more information.
     */
    ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type type __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery, field) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery, type) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery) == 16, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery) == 4, "wrong alignment");

/**
 * This structure is used to hold the result from
 * IConfigurable::querySupportedValues().
 */
struct FieldSupportedValuesQueryResult final {
    /**
     * Result of the query. Possible values are
     * - `OK`: The query was successful.
     * - `BAD_STATE`: The query was requested when the `IConfigurable` instance
     *   was in a bad state.
     * - `BAD_INDEX`: The requested field was not recognized.
     * - `TIMED_OUT`: The query could not be completed in a timely manner.
     * - `BLOCKING`: The query must block, but the parameter `mayBlock` in the
     *   call to `querySupportedValues()` was `false`.
     * - `CORRUPTED`: Some unknown error occurred.
     */
    ::android::hardware::media::c2::V1_0::Status status __attribute__ ((aligned(4)));
    /**
     * Supported values. This is meaningful only when #status is `OK`.
     */
    ::android::hardware::media::c2::V1_0::FieldSupportedValues values __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult, status) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult, values) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult) == 56, "wrong size");
static_assert(__alignof(::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::media::c2::V1_0::Status o);
static inline void PrintTo(::android::hardware::media::c2::V1_0::Status o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::media::c2::V1_0::Status lhs, const ::android::hardware::media::c2::V1_0::Status rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::media::c2::V1_0::Status rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::media::c2::V1_0::Status lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::media::c2::V1_0::Status lhs, const ::android::hardware::media::c2::V1_0::Status rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::media::c2::V1_0::Status rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::media::c2::V1_0::Status lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::media::c2::V1_0::Status e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::media::c2::V1_0::Status e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldId& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldId& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldId& lhs, const ::android::hardware::media::c2::V1_0::FieldId& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldId& lhs, const ::android::hardware::media::c2::V1_0::FieldId& rhs);

static inline std::string toString(const ::android::hardware::media::c2::V1_0::ParamField& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::ParamField& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::ParamField& lhs, const ::android::hardware::media::c2::V1_0::ParamField& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::ParamField& lhs, const ::android::hardware::media::c2::V1_0::ParamField& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib o);
static inline void PrintTo(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib lhs, const ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib lhs, const ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::ParamDescriptor& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::ParamDescriptor& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::ParamDescriptor& lhs, const ::android::hardware::media::c2::V1_0::ParamDescriptor& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::ParamDescriptor& lhs, const ::android::hardware::media::c2::V1_0::ParamDescriptor& rhs);

static inline std::string toString(const ::android::hardware::media::c2::V1_0::ValueRange& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::ValueRange& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::ValueRange& lhs, const ::android::hardware::media::c2::V1_0::ValueRange& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::ValueRange& lhs, const ::android::hardware::media::c2::V1_0::ValueRange& rhs);

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldSupportedValues& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldSupportedValues& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldSupportedValues& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValues& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldSupportedValues& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValues& rhs);

static inline std::string toString(const ::android::hardware::media::c2::V1_0::ParamFieldValues& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::ParamFieldValues& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::ParamFieldValues& lhs, const ::android::hardware::media::c2::V1_0::ParamFieldValues& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::ParamFieldValues& lhs, const ::android::hardware::media::c2::V1_0::ParamFieldValues& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::media::c2::V1_0::FieldDescriptor::Type o);
static inline void PrintTo(::android::hardware::media::c2::V1_0::FieldDescriptor::Type o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::FieldDescriptor::Type lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor::Type rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor::Type rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::FieldDescriptor::Type lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::FieldDescriptor::Type lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor::Type rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor::Type rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::FieldDescriptor::Type lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::media::c2::V1_0::FieldDescriptor::Type e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::media::c2::V1_0::FieldDescriptor::Type e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& rhs);

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldDescriptor& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldDescriptor& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldDescriptor& lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldDescriptor& lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor& rhs);

static inline std::string toString(const ::android::hardware::media::c2::V1_0::StructDescriptor& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::StructDescriptor& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::StructDescriptor& lhs, const ::android::hardware::media::c2::V1_0::StructDescriptor& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::StructDescriptor& lhs, const ::android::hardware::media::c2::V1_0::StructDescriptor& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::media::c2::V1_0::SettingResult::Failure o);
static inline void PrintTo(::android::hardware::media::c2::V1_0::SettingResult::Failure o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::SettingResult::Failure lhs, const ::android::hardware::media::c2::V1_0::SettingResult::Failure rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::SettingResult::Failure rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::SettingResult::Failure lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::SettingResult::Failure lhs, const ::android::hardware::media::c2::V1_0::SettingResult::Failure rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::SettingResult::Failure rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::SettingResult::Failure lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::media::c2::V1_0::SettingResult::Failure e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::media::c2::V1_0::SettingResult::Failure e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::SettingResult& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::SettingResult& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::SettingResult& lhs, const ::android::hardware::media::c2::V1_0::SettingResult& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::SettingResult& lhs, const ::android::hardware::media::c2::V1_0::SettingResult& rhs);

static inline std::string toString(const ::android::hardware::media::c2::V1_0::WorkOrdinal& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::WorkOrdinal& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::WorkOrdinal& lhs, const ::android::hardware::media::c2::V1_0::WorkOrdinal& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::WorkOrdinal& lhs, const ::android::hardware::media::c2::V1_0::WorkOrdinal& rhs);

static inline std::string toString(const ::android::hardware::media::c2::V1_0::BaseBlock& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::BaseBlock& o, ::std::ostream*);
// operator== and operator!= are not generated for BaseBlock

static inline std::string toString(const ::android::hardware::media::c2::V1_0::Block& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::Block& o, ::std::ostream*);
// operator== and operator!= are not generated for Block

static inline std::string toString(const ::android::hardware::media::c2::V1_0::Buffer& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::Buffer& o, ::std::ostream*);
// operator== and operator!= are not generated for Buffer

static inline std::string toString(const ::android::hardware::media::c2::V1_0::InfoBuffer& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::InfoBuffer& o, ::std::ostream*);
// operator== and operator!= are not generated for InfoBuffer

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::media::c2::V1_0::FrameData::Flags o);
static inline void PrintTo(::android::hardware::media::c2::V1_0::FrameData::Flags o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::FrameData::Flags lhs, const ::android::hardware::media::c2::V1_0::FrameData::Flags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::FrameData::Flags rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::FrameData::Flags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::FrameData::Flags lhs, const ::android::hardware::media::c2::V1_0::FrameData::Flags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::FrameData::Flags rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::FrameData::Flags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::media::c2::V1_0::FrameData::Flags e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::media::c2::V1_0::FrameData::Flags e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FrameData& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FrameData& o, ::std::ostream*);
// operator== and operator!= are not generated for FrameData

static inline std::string toString(const ::android::hardware::media::c2::V1_0::Worklet& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::Worklet& o, ::std::ostream*);
// operator== and operator!= are not generated for Worklet

static inline std::string toString(const ::android::hardware::media::c2::V1_0::Work& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::Work& o, ::std::ostream*);
// operator== and operator!= are not generated for Work

static inline std::string toString(const ::android::hardware::media::c2::V1_0::WorkBundle& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::WorkBundle& o, ::std::ostream*);
// operator== and operator!= are not generated for WorkBundle

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type o);
static inline void PrintTo(::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& rhs);

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& o);
static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& rhs);
static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::media::c2::V1_0::Status>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::media::c2::V1_0::Status::OK) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::OK;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::BAD_VALUE) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "BAD_VALUE";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::BAD_VALUE;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::BAD_INDEX) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::BAD_INDEX)) {
        os += (first ? "" : " | ");
        os += "BAD_INDEX";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::BAD_INDEX;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::CANNOT_DO) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::CANNOT_DO)) {
        os += (first ? "" : " | ");
        os += "CANNOT_DO";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::CANNOT_DO;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::DUPLICATE) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::DUPLICATE)) {
        os += (first ? "" : " | ");
        os += "DUPLICATE";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::DUPLICATE;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::NOT_FOUND) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::NOT_FOUND)) {
        os += (first ? "" : " | ");
        os += "NOT_FOUND";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::NOT_FOUND;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::BAD_STATE) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::BAD_STATE)) {
        os += (first ? "" : " | ");
        os += "BAD_STATE";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::BAD_STATE;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::BLOCKING) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::BLOCKING)) {
        os += (first ? "" : " | ");
        os += "BLOCKING";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::BLOCKING;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::NO_MEMORY) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::NO_MEMORY)) {
        os += (first ? "" : " | ");
        os += "NO_MEMORY";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::NO_MEMORY;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::REFUSED) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::REFUSED)) {
        os += (first ? "" : " | ");
        os += "REFUSED";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::REFUSED;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::TIMED_OUT) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::TIMED_OUT)) {
        os += (first ? "" : " | ");
        os += "TIMED_OUT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::TIMED_OUT;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::OMITTED) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::OMITTED)) {
        os += (first ? "" : " | ");
        os += "OMITTED";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::OMITTED;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::CORRUPTED) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::CORRUPTED)) {
        os += (first ? "" : " | ");
        os += "CORRUPTED";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::CORRUPTED;
    }
    if ((o & ::android::hardware::media::c2::V1_0::Status::NO_INIT) == static_cast<int32_t>(::android::hardware::media::c2::V1_0::Status::NO_INIT)) {
        os += (first ? "" : " | ");
        os += "NO_INIT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::Status::NO_INIT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::media::c2::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::media::c2::V1_0::Status::OK) {
        return "OK";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::BAD_VALUE) {
        return "BAD_VALUE";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::BAD_INDEX) {
        return "BAD_INDEX";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::CANNOT_DO) {
        return "CANNOT_DO";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::DUPLICATE) {
        return "DUPLICATE";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::NOT_FOUND) {
        return "NOT_FOUND";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::BAD_STATE) {
        return "BAD_STATE";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::BLOCKING) {
        return "BLOCKING";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::NO_MEMORY) {
        return "NO_MEMORY";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::REFUSED) {
        return "REFUSED";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::TIMED_OUT) {
        return "TIMED_OUT";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::OMITTED) {
        return "OMITTED";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::CORRUPTED) {
        return "CORRUPTED";
    }
    if (o == ::android::hardware::media::c2::V1_0::Status::NO_INIT) {
        return "NO_INIT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::media::c2::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldId& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".offset = ";
    os += ::android::hardware::toString(o.offset);
    os += ", .size = ";
    os += ::android::hardware::toString(o.size);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldId& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldId& lhs, const ::android::hardware::media::c2::V1_0::FieldId& rhs) {
    if (lhs.offset != rhs.offset) {
        return false;
    }
    if (lhs.size != rhs.size) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldId& lhs, const ::android::hardware::media::c2::V1_0::FieldId& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::ParamField& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".index = ";
    os += ::android::hardware::toString(o.index);
    os += ", .fieldId = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.fieldId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::ParamField& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::ParamField& lhs, const ::android::hardware::media::c2::V1_0::ParamField& rhs) {
    if (lhs.index != rhs.index) {
        return false;
    }
    if (lhs.fieldId != rhs.fieldId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::ParamField& lhs, const ::android::hardware::media::c2::V1_0::ParamField& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::REQUIRED) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::REQUIRED)) {
        os += (first ? "" : " | ");
        os += "REQUIRED";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::REQUIRED;
    }
    if ((o & ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::PERSISTENT) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::PERSISTENT)) {
        os += (first ? "" : " | ");
        os += "PERSISTENT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::PERSISTENT;
    }
    if ((o & ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::STRICT) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::STRICT)) {
        os += (first ? "" : " | ");
        os += "STRICT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::STRICT;
    }
    if ((o & ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::READ_ONLY) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::READ_ONLY)) {
        os += (first ? "" : " | ");
        os += "READ_ONLY";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::READ_ONLY;
    }
    if ((o & ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::HIDDEN) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::HIDDEN)) {
        os += (first ? "" : " | ");
        os += "HIDDEN";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::HIDDEN;
    }
    if ((o & ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::INTERNAL) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::INTERNAL)) {
        os += (first ? "" : " | ");
        os += "INTERNAL";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::INTERNAL;
    }
    if ((o & ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::CONST) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::CONST)) {
        os += (first ? "" : " | ");
        os += "CONST";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::CONST;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::REQUIRED) {
        return "REQUIRED";
    }
    if (o == ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::PERSISTENT) {
        return "PERSISTENT";
    }
    if (o == ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::STRICT) {
        return "STRICT";
    }
    if (o == ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::READ_ONLY) {
        return "READ_ONLY";
    }
    if (o == ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::HIDDEN) {
        return "HIDDEN";
    }
    if (o == ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::INTERNAL) {
        return "INTERNAL";
    }
    if (o == ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::CONST) {
        return "CONST";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::ParamDescriptor& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".index = ";
    os += ::android::hardware::toString(o.index);
    os += ", .attrib = ";
    os += ::android::hardware::media::c2::V1_0::toString<::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib>(o.attrib);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .dependencies = ";
    os += ::android::hardware::toString(o.dependencies);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::ParamDescriptor& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::ParamDescriptor& lhs, const ::android::hardware::media::c2::V1_0::ParamDescriptor& rhs) {
    if (lhs.index != rhs.index) {
        return false;
    }
    if (lhs.attrib != rhs.attrib) {
        return false;
    }
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.dependencies != rhs.dependencies) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::ParamDescriptor& lhs, const ::android::hardware::media::c2::V1_0::ParamDescriptor& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::ValueRange& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".min = ";
    os += ::android::hardware::toString(o.min);
    os += ", .max = ";
    os += ::android::hardware::toString(o.max);
    os += ", .step = ";
    os += ::android::hardware::toString(o.step);
    os += ", .num = ";
    os += ::android::hardware::toString(o.num);
    os += ", .denom = ";
    os += ::android::hardware::toString(o.denom);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::ValueRange& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::ValueRange& lhs, const ::android::hardware::media::c2::V1_0::ValueRange& rhs) {
    if (lhs.min != rhs.min) {
        return false;
    }
    if (lhs.max != rhs.max) {
        return false;
    }
    if (lhs.step != rhs.step) {
        return false;
    }
    if (lhs.num != rhs.num) {
        return false;
    }
    if (lhs.denom != rhs.denom) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::ValueRange& lhs, const ::android::hardware::media::c2::V1_0::ValueRange& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldSupportedValues& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_discriminator::empty: {
            os += ".empty = ";
            os += toString(o.empty());
            break;
        }
        case ::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_discriminator::range: {
            os += ".range = ";
            os += toString(o.range());
            break;
        }
        case ::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_discriminator::values: {
            os += ".values = ";
            os += toString(o.values());
            break;
        }
        case ::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_discriminator::flags: {
            os += ".flags = ";
            os += toString(o.flags());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldSupportedValues& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldSupportedValues& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValues& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_discriminator::empty: {
            return (lhs.empty() == rhs.empty());
        }
        case ::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_discriminator::range: {
            return (lhs.range() == rhs.range());
        }
        case ::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_discriminator::values: {
            return (lhs.values() == rhs.values());
        }
        case ::android::hardware::media::c2::V1_0::FieldSupportedValues::hidl_discriminator::flags: {
            return (lhs.flags() == rhs.flags());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldSupportedValues& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValues& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::ParamFieldValues& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".paramOrField = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.paramOrField);
    os += ", .values = ";
    os += ::android::hardware::toString(o.values);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::ParamFieldValues& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::ParamFieldValues& lhs, const ::android::hardware::media::c2::V1_0::ParamFieldValues& rhs) {
    if (lhs.paramOrField != rhs.paramOrField) {
        return false;
    }
    if (lhs.values != rhs.values) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::ParamFieldValues& lhs, const ::android::hardware::media::c2::V1_0::ParamFieldValues& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::media::c2::V1_0::FieldDescriptor::Type>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::FieldDescriptor::Type> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::NO_INIT) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::NO_INIT)) {
        os += (first ? "" : " | ");
        os += "NO_INIT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::NO_INIT;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::INT32) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::INT32)) {
        os += (first ? "" : " | ");
        os += "INT32";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::INT32;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::UINT32) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::UINT32)) {
        os += (first ? "" : " | ");
        os += "UINT32";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::UINT32;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::CNTR32) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::CNTR32)) {
        os += (first ? "" : " | ");
        os += "CNTR32";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::CNTR32;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::INT64) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::INT64)) {
        os += (first ? "" : " | ");
        os += "INT64";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::INT64;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::UINT64) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::UINT64)) {
        os += (first ? "" : " | ");
        os += "UINT64";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::UINT64;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::CNTR64) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::CNTR64)) {
        os += (first ? "" : " | ");
        os += "CNTR64";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::CNTR64;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::FLOAT) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::FLOAT)) {
        os += (first ? "" : " | ");
        os += "FLOAT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::FLOAT;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::STRING) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::STRING)) {
        os += (first ? "" : " | ");
        os += "STRING";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::STRING;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::BLOB) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::BLOB)) {
        os += (first ? "" : " | ");
        os += "BLOB";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::BLOB;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::STRUCT) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldDescriptor::Type::STRUCT)) {
        os += (first ? "" : " | ");
        os += "STRUCT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::STRUCT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::media::c2::V1_0::FieldDescriptor::Type o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::NO_INIT) {
        return "NO_INIT";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::INT32) {
        return "INT32";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::UINT32) {
        return "UINT32";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::CNTR32) {
        return "CNTR32";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::INT64) {
        return "INT64";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::UINT64) {
        return "UINT64";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::CNTR64) {
        return "CNTR64";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::FLOAT) {
        return "FLOAT";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::STRING) {
        return "STRING";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::BLOB) {
        return "BLOB";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::STRUCT) {
        return "STRUCT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::media::c2::V1_0::FieldDescriptor::Type o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor::NamedValue& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldDescriptor& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".fieldId = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.fieldId);
    os += ", .type = ";
    os += ::android::hardware::media::c2::V1_0::toString<::android::hardware::media::c2::V1_0::FieldDescriptor::Type>(o.type);
    os += ", .structIndex = ";
    os += ::android::hardware::toString(o.structIndex);
    os += ", .extent = ";
    os += ::android::hardware::toString(o.extent);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .namedValues = ";
    os += ::android::hardware::toString(o.namedValues);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldDescriptor& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldDescriptor& lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor& rhs) {
    if (lhs.fieldId != rhs.fieldId) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.structIndex != rhs.structIndex) {
        return false;
    }
    if (lhs.extent != rhs.extent) {
        return false;
    }
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.namedValues != rhs.namedValues) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldDescriptor& lhs, const ::android::hardware::media::c2::V1_0::FieldDescriptor& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::StructDescriptor& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::toString(o.type);
    os += ", .fields = ";
    os += ::android::hardware::toString(o.fields);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::StructDescriptor& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::StructDescriptor& lhs, const ::android::hardware::media::c2::V1_0::StructDescriptor& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.fields != rhs.fields) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::StructDescriptor& lhs, const ::android::hardware::media::c2::V1_0::StructDescriptor& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::media::c2::V1_0::SettingResult::Failure>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::SettingResult::Failure> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_TYPE) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_TYPE)) {
        os += (first ? "" : " | ");
        os += "BAD_TYPE";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_TYPE;
    }
    if ((o & ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_PORT) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_PORT)) {
        os += (first ? "" : " | ");
        os += "BAD_PORT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_PORT;
    }
    if ((o & ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_INDEX) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_INDEX)) {
        os += (first ? "" : " | ");
        os += "BAD_INDEX";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_INDEX;
    }
    if ((o & ::android::hardware::media::c2::V1_0::SettingResult::Failure::READ_ONLY) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::SettingResult::Failure::READ_ONLY)) {
        os += (first ? "" : " | ");
        os += "READ_ONLY";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::SettingResult::Failure::READ_ONLY;
    }
    if ((o & ::android::hardware::media::c2::V1_0::SettingResult::Failure::MISMATCH) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::SettingResult::Failure::MISMATCH)) {
        os += (first ? "" : " | ");
        os += "MISMATCH";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::SettingResult::Failure::MISMATCH;
    }
    if ((o & ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_VALUE) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "BAD_VALUE";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_VALUE;
    }
    if ((o & ::android::hardware::media::c2::V1_0::SettingResult::Failure::CONFLICT) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::SettingResult::Failure::CONFLICT)) {
        os += (first ? "" : " | ");
        os += "CONFLICT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::SettingResult::Failure::CONFLICT;
    }
    if ((o & ::android::hardware::media::c2::V1_0::SettingResult::Failure::UNSUPPORTED) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::SettingResult::Failure::UNSUPPORTED)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::SettingResult::Failure::UNSUPPORTED;
    }
    if ((o & ::android::hardware::media::c2::V1_0::SettingResult::Failure::INFO_BAD_VALUE) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::SettingResult::Failure::INFO_BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "INFO_BAD_VALUE";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::SettingResult::Failure::INFO_BAD_VALUE;
    }
    if ((o & ::android::hardware::media::c2::V1_0::SettingResult::Failure::INFO_CONFLICT) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::SettingResult::Failure::INFO_CONFLICT)) {
        os += (first ? "" : " | ");
        os += "INFO_CONFLICT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::SettingResult::Failure::INFO_CONFLICT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::media::c2::V1_0::SettingResult::Failure o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_TYPE) {
        return "BAD_TYPE";
    }
    if (o == ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_PORT) {
        return "BAD_PORT";
    }
    if (o == ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_INDEX) {
        return "BAD_INDEX";
    }
    if (o == ::android::hardware::media::c2::V1_0::SettingResult::Failure::READ_ONLY) {
        return "READ_ONLY";
    }
    if (o == ::android::hardware::media::c2::V1_0::SettingResult::Failure::MISMATCH) {
        return "MISMATCH";
    }
    if (o == ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_VALUE) {
        return "BAD_VALUE";
    }
    if (o == ::android::hardware::media::c2::V1_0::SettingResult::Failure::CONFLICT) {
        return "CONFLICT";
    }
    if (o == ::android::hardware::media::c2::V1_0::SettingResult::Failure::UNSUPPORTED) {
        return "UNSUPPORTED";
    }
    if (o == ::android::hardware::media::c2::V1_0::SettingResult::Failure::INFO_BAD_VALUE) {
        return "INFO_BAD_VALUE";
    }
    if (o == ::android::hardware::media::c2::V1_0::SettingResult::Failure::INFO_CONFLICT) {
        return "INFO_CONFLICT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::media::c2::V1_0::SettingResult::Failure o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::SettingResult& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".failure = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.failure);
    os += ", .field = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.field);
    os += ", .conflicts = ";
    os += ::android::hardware::toString(o.conflicts);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::SettingResult& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::SettingResult& lhs, const ::android::hardware::media::c2::V1_0::SettingResult& rhs) {
    if (lhs.failure != rhs.failure) {
        return false;
    }
    if (lhs.field != rhs.field) {
        return false;
    }
    if (lhs.conflicts != rhs.conflicts) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::SettingResult& lhs, const ::android::hardware::media::c2::V1_0::SettingResult& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::WorkOrdinal& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timestampUs = ";
    os += ::android::hardware::toString(o.timestampUs);
    os += ", .frameIndex = ";
    os += ::android::hardware::toString(o.frameIndex);
    os += ", .customOrdinal = ";
    os += ::android::hardware::toString(o.customOrdinal);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::WorkOrdinal& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::WorkOrdinal& lhs, const ::android::hardware::media::c2::V1_0::WorkOrdinal& rhs) {
    if (lhs.timestampUs != rhs.timestampUs) {
        return false;
    }
    if (lhs.frameIndex != rhs.frameIndex) {
        return false;
    }
    if (lhs.customOrdinal != rhs.customOrdinal) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::WorkOrdinal& lhs, const ::android::hardware::media::c2::V1_0::WorkOrdinal& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::BaseBlock& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::media::c2::V1_0::BaseBlock::hidl_discriminator::nativeBlock: {
            os += ".nativeBlock = ";
            os += toString(o.nativeBlock());
            break;
        }
        case ::android::hardware::media::c2::V1_0::BaseBlock::hidl_discriminator::pooledBlock: {
            os += ".pooledBlock = ";
            os += toString(o.pooledBlock());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::BaseBlock& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for BaseBlock

static inline std::string toString(const ::android::hardware::media::c2::V1_0::Block& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".index = ";
    os += ::android::hardware::toString(o.index);
    os += ", .meta = ";
    os += ::android::hardware::toString(o.meta);
    os += ", .fence = ";
    os += ::android::hardware::toString(o.fence);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::Block& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Block

static inline std::string toString(const ::android::hardware::media::c2::V1_0::Buffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".info = ";
    os += ::android::hardware::toString(o.info);
    os += ", .blocks = ";
    os += ::android::hardware::toString(o.blocks);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::Buffer& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Buffer

static inline std::string toString(const ::android::hardware::media::c2::V1_0::InfoBuffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".index = ";
    os += ::android::hardware::toString(o.index);
    os += ", .buffer = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.buffer);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::InfoBuffer& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for InfoBuffer

template<>
inline std::string toString<::android::hardware::media::c2::V1_0::FrameData::Flags>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::FrameData::Flags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::media::c2::V1_0::FrameData::Flags::DROP_FRAME) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FrameData::Flags::DROP_FRAME)) {
        os += (first ? "" : " | ");
        os += "DROP_FRAME";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FrameData::Flags::DROP_FRAME;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FrameData::Flags::END_OF_STREAM) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FrameData::Flags::END_OF_STREAM)) {
        os += (first ? "" : " | ");
        os += "END_OF_STREAM";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FrameData::Flags::END_OF_STREAM;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FrameData::Flags::DISCARD_FRAME) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FrameData::Flags::DISCARD_FRAME)) {
        os += (first ? "" : " | ");
        os += "DISCARD_FRAME";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FrameData::Flags::DISCARD_FRAME;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FrameData::Flags::FLAG_INCOMPLETE) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FrameData::Flags::FLAG_INCOMPLETE)) {
        os += (first ? "" : " | ");
        os += "FLAG_INCOMPLETE";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FrameData::Flags::FLAG_INCOMPLETE;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FrameData::Flags::CODEC_CONFIG) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FrameData::Flags::CODEC_CONFIG)) {
        os += (first ? "" : " | ");
        os += "CODEC_CONFIG";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FrameData::Flags::CODEC_CONFIG;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::media::c2::V1_0::FrameData::Flags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::media::c2::V1_0::FrameData::Flags::DROP_FRAME) {
        return "DROP_FRAME";
    }
    if (o == ::android::hardware::media::c2::V1_0::FrameData::Flags::END_OF_STREAM) {
        return "END_OF_STREAM";
    }
    if (o == ::android::hardware::media::c2::V1_0::FrameData::Flags::DISCARD_FRAME) {
        return "DISCARD_FRAME";
    }
    if (o == ::android::hardware::media::c2::V1_0::FrameData::Flags::FLAG_INCOMPLETE) {
        return "FLAG_INCOMPLETE";
    }
    if (o == ::android::hardware::media::c2::V1_0::FrameData::Flags::CODEC_CONFIG) {
        return "CODEC_CONFIG";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::media::c2::V1_0::FrameData::Flags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FrameData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".flags = ";
    os += ::android::hardware::media::c2::V1_0::toString<::android::hardware::media::c2::V1_0::FrameData::Flags>(o.flags);
    os += ", .ordinal = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.ordinal);
    os += ", .buffers = ";
    os += ::android::hardware::toString(o.buffers);
    os += ", .configUpdate = ";
    os += ::android::hardware::toString(o.configUpdate);
    os += ", .infoBuffers = ";
    os += ::android::hardware::toString(o.infoBuffers);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FrameData& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for FrameData

static inline std::string toString(const ::android::hardware::media::c2::V1_0::Worklet& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".componentId = ";
    os += ::android::hardware::toString(o.componentId);
    os += ", .tunings = ";
    os += ::android::hardware::toString(o.tunings);
    os += ", .failures = ";
    os += ::android::hardware::toString(o.failures);
    os += ", .output = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.output);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::Worklet& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Worklet

static inline std::string toString(const ::android::hardware::media::c2::V1_0::Work& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".chainInfo = ";
    os += ::android::hardware::toString(o.chainInfo);
    os += ", .input = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.input);
    os += ", .worklets = ";
    os += ::android::hardware::toString(o.worklets);
    os += ", .workletsProcessed = ";
    os += ::android::hardware::toString(o.workletsProcessed);
    os += ", .result = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.result);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::Work& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Work

static inline std::string toString(const ::android::hardware::media::c2::V1_0::WorkBundle& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".works = ";
    os += ::android::hardware::toString(o.works);
    os += ", .baseBlocks = ";
    os += ::android::hardware::toString(o.baseBlocks);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::WorkBundle& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for WorkBundle

template<>
inline std::string toString<::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type::POSSIBLE) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type::POSSIBLE)) {
        os += (first ? "" : " | ");
        os += "POSSIBLE";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type::POSSIBLE;
    }
    if ((o & ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type::CURRENT) == static_cast<uint32_t>(::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type::CURRENT)) {
        os += (first ? "" : " | ");
        os += "CURRENT";
        first = false;
        flipped |= ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type::CURRENT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type::POSSIBLE) {
        return "POSSIBLE";
    }
    if (o == ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type::CURRENT) {
        return "CURRENT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".field = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.field);
    os += ", .type = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.type);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& rhs) {
    if (lhs.field != rhs.field) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.status);
    os += ", .values = ";
    os += ::android::hardware::media::c2::V1_0::toString(o.values);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& rhs) {
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.values != rhs.values) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& lhs, const ::android::hardware::media::c2::V1_0::FieldSupportedValuesQueryResult& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace c2
}  // namespace media
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::media::c2::V1_0::Status, 14> hidl_enum_values<::android::hardware::media::c2::V1_0::Status> = {
    ::android::hardware::media::c2::V1_0::Status::OK,
    ::android::hardware::media::c2::V1_0::Status::BAD_VALUE,
    ::android::hardware::media::c2::V1_0::Status::BAD_INDEX,
    ::android::hardware::media::c2::V1_0::Status::CANNOT_DO,
    ::android::hardware::media::c2::V1_0::Status::DUPLICATE,
    ::android::hardware::media::c2::V1_0::Status::NOT_FOUND,
    ::android::hardware::media::c2::V1_0::Status::BAD_STATE,
    ::android::hardware::media::c2::V1_0::Status::BLOCKING,
    ::android::hardware::media::c2::V1_0::Status::NO_MEMORY,
    ::android::hardware::media::c2::V1_0::Status::REFUSED,
    ::android::hardware::media::c2::V1_0::Status::TIMED_OUT,
    ::android::hardware::media::c2::V1_0::Status::OMITTED,
    ::android::hardware::media::c2::V1_0::Status::CORRUPTED,
    ::android::hardware::media::c2::V1_0::Status::NO_INIT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib, 7> hidl_enum_values<::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib> = {
    ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::REQUIRED,
    ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::PERSISTENT,
    ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::STRICT,
    ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::READ_ONLY,
    ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::HIDDEN,
    ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::INTERNAL,
    ::android::hardware::media::c2::V1_0::ParamDescriptor::Attrib::CONST,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::media::c2::V1_0::FieldDescriptor::Type, 11> hidl_enum_values<::android::hardware::media::c2::V1_0::FieldDescriptor::Type> = {
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::NO_INIT,
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::INT32,
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::UINT32,
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::CNTR32,
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::INT64,
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::UINT64,
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::CNTR64,
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::FLOAT,
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::STRING,
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::BLOB,
    ::android::hardware::media::c2::V1_0::FieldDescriptor::Type::STRUCT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::media::c2::V1_0::SettingResult::Failure, 10> hidl_enum_values<::android::hardware::media::c2::V1_0::SettingResult::Failure> = {
    ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_TYPE,
    ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_PORT,
    ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_INDEX,
    ::android::hardware::media::c2::V1_0::SettingResult::Failure::READ_ONLY,
    ::android::hardware::media::c2::V1_0::SettingResult::Failure::MISMATCH,
    ::android::hardware::media::c2::V1_0::SettingResult::Failure::BAD_VALUE,
    ::android::hardware::media::c2::V1_0::SettingResult::Failure::CONFLICT,
    ::android::hardware::media::c2::V1_0::SettingResult::Failure::UNSUPPORTED,
    ::android::hardware::media::c2::V1_0::SettingResult::Failure::INFO_BAD_VALUE,
    ::android::hardware::media::c2::V1_0::SettingResult::Failure::INFO_CONFLICT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::media::c2::V1_0::FrameData::Flags, 5> hidl_enum_values<::android::hardware::media::c2::V1_0::FrameData::Flags> = {
    ::android::hardware::media::c2::V1_0::FrameData::Flags::DROP_FRAME,
    ::android::hardware::media::c2::V1_0::FrameData::Flags::END_OF_STREAM,
    ::android::hardware::media::c2::V1_0::FrameData::Flags::DISCARD_FRAME,
    ::android::hardware::media::c2::V1_0::FrameData::Flags::FLAG_INCOMPLETE,
    ::android::hardware::media::c2::V1_0::FrameData::Flags::CODEC_CONFIG,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type, 2> hidl_enum_values<::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type> = {
    ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type::POSSIBLE,
    ::android::hardware::media::c2::V1_0::FieldSupportedValuesQuery::Type::CURRENT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_TYPES_H
