#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_1_TYPES_H

#include <android/hardware/drm/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_1 {

// Forward declaration for forward reference support:
struct DrmMetricGroup;
enum class HdcpLevel : uint32_t;
enum class KeyRequestType : uint32_t;
enum class SecurityLevel : uint32_t;
struct SecureStopRelease;

/**
 * This message contains plugin-specific metrics made available to the client.
 * The message is used for making vendor-specific metrics available to an
 * application. The framework is not consuming any of the information.
 *
 * Metrics are grouped in instances of DrmMetricGroup. Each group contains
 * multiple instances of Metric.
 *
 * Example:
 *
 * Capture the timing information of a buffer copy event, "buf_copy", broken
 * out by the "size" of the buffer.
 *
 * DrmMetricGroup {
 *   metrics[0] {
 *     name: "buf_copy"
 *     attributes[0] {
 *       name: "size"
 *       type: INT64_TYPE
 *       int64Value: 1024
 *     }
 *     values[0] {
 *       componentName: "operation_count"
 *       type: INT64_TYPE
 *       int64Value: 75
 *     }
 *     values[1] {
 *       component_name: "average_time_seconds"
 *       type: DOUBLE_TYPE
 *       doubleValue: 0.00000042
 *     }
 *   }
 * }
 */
struct DrmMetricGroup final {
    // Forward declaration for forward reference support:
    enum class ValueType : uint8_t;
    struct Attribute;
    struct Value;
    struct Metric;

    /**
     * Used to discriminate the type of value being stored in the structs
     * below.
     */
    enum class ValueType : uint8_t {
        INT64_TYPE = 0,
        DOUBLE_TYPE = 1 /* ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType.INT64_TYPE implicitly + 1 */,
        STRING_TYPE = 2 /* ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType.DOUBLE_TYPE implicitly + 1 */,
    };

    /**
     * A detail about the metric being captured. The fields of an Attribute
     * are opaque to the framework.
     */
    struct Attribute final {
        ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
        /**
         * The type field indicates which of the following values is used.
         */
        ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType type __attribute__ ((aligned(1)));
        int64_t int64Value __attribute__ ((aligned(8)));
        double doubleValue __attribute__ ((aligned(8)));
        ::android::hardware::hidl_string stringValue __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Attribute, name) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Attribute, type) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Attribute, int64Value) == 24, "wrong offset");
    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Attribute, doubleValue) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Attribute, stringValue) == 40, "wrong offset");
    static_assert(sizeof(::android::hardware::drm::V1_1::DrmMetricGroup::Attribute) == 56, "wrong size");
    static_assert(__alignof(::android::hardware::drm::V1_1::DrmMetricGroup::Attribute) == 8, "wrong alignment");

    /**
     * A value of the metric. A metric may have multiple values. The
     * component name may be left empty if there is only supposed to be
     * one value for the given metric. The fields of the Value are
     * opaque to the framework.
     */
    struct Value final {
        ::android::hardware::hidl_string componentName __attribute__ ((aligned(8)));
        /**
         * The type field indicates which of the following values is used.
         */
        ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType type __attribute__ ((aligned(1)));
        int64_t int64Value __attribute__ ((aligned(8)));
        double doubleValue __attribute__ ((aligned(8)));
        ::android::hardware::hidl_string stringValue __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Value, componentName) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Value, type) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Value, int64Value) == 24, "wrong offset");
    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Value, doubleValue) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Value, stringValue) == 40, "wrong offset");
    static_assert(sizeof(::android::hardware::drm::V1_1::DrmMetricGroup::Value) == 56, "wrong size");
    static_assert(__alignof(::android::hardware::drm::V1_1::DrmMetricGroup::Value) == 8, "wrong alignment");

    /**
     * The metric being captured. A metric must have a name and at least one
     * value. A metric may have 0 or more attributes. The fields of a Metric
     * are opaque to the framework.
     */
    struct Metric final {
        ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<::android::hardware::drm::V1_1::DrmMetricGroup::Attribute> attributes __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<::android::hardware::drm::V1_1::DrmMetricGroup::Value> values __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Metric, name) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Metric, attributes) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup::Metric, values) == 32, "wrong offset");
    static_assert(sizeof(::android::hardware::drm::V1_1::DrmMetricGroup::Metric) == 48, "wrong size");
    static_assert(__alignof(::android::hardware::drm::V1_1::DrmMetricGroup::Metric) == 8, "wrong alignment");

    /**
     * The list of metrics to be captured.
     */
    ::android::hardware::hidl_vec<::android::hardware::drm::V1_1::DrmMetricGroup::Metric> metrics __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::drm::V1_1::DrmMetricGroup, metrics) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::drm::V1_1::DrmMetricGroup) == 16, "wrong size");
static_assert(__alignof(::android::hardware::drm::V1_1::DrmMetricGroup) == 8, "wrong alignment");

/**
 * HDCP specifications are defined by Digital Content Protection LLC (DCP).
 *   "HDCP Specification Rev. 2.2 Interface Independent Adaptation"
 *   "HDCP 2.2 on HDMI Specification"
 */
enum class HdcpLevel : uint32_t {
    /**
     * Unable to determine the HDCP level
     */
    HDCP_UNKNOWN = 0u,
    /**
     * No HDCP, output is unprotected
     */
    HDCP_NONE = 1u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_UNKNOWN implicitly + 1 */,
    /**
     * HDCP version 1.0
     */
    HDCP_V1 = 2u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_NONE implicitly + 1 */,
    /**
     * HDCP version 2.0 Type 1.
     */
    HDCP_V2 = 3u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_V1 implicitly + 1 */,
    /**
     * HDCP version 2.1 Type 1.
     */
    HDCP_V2_1 = 4u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_V2 implicitly + 1 */,
    /**
     *  HDCP version 2.2 Type 1.
     */
    HDCP_V2_2 = 5u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_V2_1 implicitly + 1 */,
    /**
     * No digital output, implicitly secure
     */
    HDCP_NO_OUTPUT = 6u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_V2_2 implicitly + 1 */,
};

/**
 * KeyRequestTypes (in addition to those from 1.0) which allow an app
 * to determine the type of a key request returned from getKeyRequest.
 */
enum class KeyRequestType : uint32_t {
    /**
     * Key request type is for an initial license request
     */
    INITIAL = 0u,
    /**
     * Key request type is for license renewal. Renewal requests are used
     * to extend the validity period for streaming keys.
     */
    RENEWAL = 1u /* ::android::hardware::drm::V1_0::KeyRequestType.INITIAL implicitly + 1 */,
    /**
     * Key request type is a release. A key release causes offline keys
     * to become available for streaming.
     */
    RELEASE = 2u /* ::android::hardware::drm::V1_0::KeyRequestType.RENEWAL implicitly + 1 */,
    /**
     * Key request type is unknown due to some error condition.
     */
    UNKNOWN = 3u /* ::android::hardware::drm::V1_0::KeyRequestType.RELEASE implicitly + 1 */,
    /**
     * Keys are already loaded. No key request is needed.
     */
    NONE = 4u /* ::android::hardware::drm::V1_0::KeyRequestType.UNKNOWN implicitly + 1 */,
    /**
     * Keys have previously been loaded. An additional (non-renewal) license
     * request is needed.
     */
    UPDATE = 5u /* ::android::hardware::drm::V1_1::KeyRequestType.NONE implicitly + 1 */,
};

enum class SecurityLevel : uint32_t {
    /**
     * Unable to determine the security level
     */
    UNKNOWN = 0u,
    /**
     * Software-based whitebox crypto
     */
    SW_SECURE_CRYPTO = 1u /* ::android::hardware::drm::V1_1::SecurityLevel.UNKNOWN implicitly + 1 */,
    /**
     * Software-based whitebox crypto and an obfuscated decoder
     */
    SW_SECURE_DECODE = 2u /* ::android::hardware::drm::V1_1::SecurityLevel.SW_SECURE_CRYPTO implicitly + 1 */,
    /**
     * DRM key management and crypto operations are performed within a
     * hardware backed trusted execution environment
     */
    HW_SECURE_CRYPTO = 3u /* ::android::hardware::drm::V1_1::SecurityLevel.SW_SECURE_DECODE implicitly + 1 */,
    /**
     * DRM key management, crypto operations and decoding of content
     * are performed within a hardware backed trusted execution environment
     */
    HW_SECURE_DECODE = 4u /* ::android::hardware::drm::V1_1::SecurityLevel.HW_SECURE_CRYPTO implicitly + 1 */,
    /**
     * DRM key management, crypto operations, decoding of content and all
     * handling of the media (compressed and uncompressed) is handled within
     * a hardware backed trusted execution environment.
     */
    HW_SECURE_ALL = 5u /* ::android::hardware::drm::V1_1::SecurityLevel.HW_SECURE_DECODE implicitly + 1 */,
};

/**
 * Encapsulates a secure stop release opaque object
 */
struct SecureStopRelease final {
    ::android::hardware::hidl_vec<uint8_t> opaqueData __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::drm::V1_1::SecureStopRelease, opaqueData) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::drm::V1_1::SecureStopRelease) == 16, "wrong size");
static_assert(__alignof(::android::hardware::drm::V1_1::SecureStopRelease) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::drm::V1_1::DrmMetricGroup::ValueType o);
static inline void PrintTo(::android::hardware::drm::V1_1::DrmMetricGroup::ValueType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& o);
static inline void PrintTo(const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& rhs);

static inline std::string toString(const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& o);
static inline void PrintTo(const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& rhs);

static inline std::string toString(const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& o);
static inline void PrintTo(const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& rhs);

static inline std::string toString(const ::android::hardware::drm::V1_1::DrmMetricGroup& o);
static inline void PrintTo(const ::android::hardware::drm::V1_1::DrmMetricGroup& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_1::DrmMetricGroup& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_1::DrmMetricGroup& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_1::HdcpLevel o);
static inline void PrintTo(::android::hardware::drm::V1_1::HdcpLevel o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_1::HdcpLevel lhs, const ::android::hardware::drm::V1_1::HdcpLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_1::HdcpLevel rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_1::HdcpLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_1::HdcpLevel lhs, const ::android::hardware::drm::V1_1::HdcpLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_1::HdcpLevel rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_1::HdcpLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_1::HdcpLevel e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_1::HdcpLevel e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_1::KeyRequestType o);
static inline void PrintTo(::android::hardware::drm::V1_1::KeyRequestType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_1::KeyRequestType lhs, const ::android::hardware::drm::V1_1::KeyRequestType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_1::KeyRequestType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_1::KeyRequestType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_1::KeyRequestType lhs, const ::android::hardware::drm::V1_1::KeyRequestType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_1::KeyRequestType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_1::KeyRequestType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_1::KeyRequestType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_1::KeyRequestType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_1::SecurityLevel o);
static inline void PrintTo(::android::hardware::drm::V1_1::SecurityLevel o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_1::SecurityLevel lhs, const ::android::hardware::drm::V1_1::SecurityLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_1::SecurityLevel rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_1::SecurityLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_1::SecurityLevel lhs, const ::android::hardware::drm::V1_1::SecurityLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_1::SecurityLevel rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_1::SecurityLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_1::SecurityLevel e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_1::SecurityLevel e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::drm::V1_1::SecureStopRelease& o);
static inline void PrintTo(const ::android::hardware::drm::V1_1::SecureStopRelease& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_1::SecureStopRelease& lhs, const ::android::hardware::drm::V1_1::SecureStopRelease& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_1::SecureStopRelease& lhs, const ::android::hardware::drm::V1_1::SecureStopRelease& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::drm::V1_1::DrmMetricGroup::ValueType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_1::DrmMetricGroup::ValueType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::INT64_TYPE) == static_cast<uint8_t>(::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::INT64_TYPE)) {
        os += (first ? "" : " | ");
        os += "INT64_TYPE";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::INT64_TYPE;
    }
    if ((o & ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::DOUBLE_TYPE) == static_cast<uint8_t>(::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::DOUBLE_TYPE)) {
        os += (first ? "" : " | ");
        os += "DOUBLE_TYPE";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::DOUBLE_TYPE;
    }
    if ((o & ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::STRING_TYPE) == static_cast<uint8_t>(::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::STRING_TYPE)) {
        os += (first ? "" : " | ");
        os += "STRING_TYPE";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::STRING_TYPE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_1::DrmMetricGroup::ValueType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::INT64_TYPE) {
        return "INT64_TYPE";
    }
    if (o == ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::DOUBLE_TYPE) {
        return "DOUBLE_TYPE";
    }
    if (o == ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::STRING_TYPE) {
        return "STRING_TYPE";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_1::DrmMetricGroup::ValueType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .type = ";
    os += ::android::hardware::drm::V1_1::toString(o.type);
    os += ", .int64Value = ";
    os += ::android::hardware::toString(o.int64Value);
    os += ", .doubleValue = ";
    os += ::android::hardware::toString(o.doubleValue);
    os += ", .stringValue = ";
    os += ::android::hardware::toString(o.stringValue);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.int64Value != rhs.int64Value) {
        return false;
    }
    if (lhs.doubleValue != rhs.doubleValue) {
        return false;
    }
    if (lhs.stringValue != rhs.stringValue) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".componentName = ";
    os += ::android::hardware::toString(o.componentName);
    os += ", .type = ";
    os += ::android::hardware::drm::V1_1::toString(o.type);
    os += ", .int64Value = ";
    os += ::android::hardware::toString(o.int64Value);
    os += ", .doubleValue = ";
    os += ::android::hardware::toString(o.doubleValue);
    os += ", .stringValue = ";
    os += ::android::hardware::toString(o.stringValue);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& rhs) {
    if (lhs.componentName != rhs.componentName) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.int64Value != rhs.int64Value) {
        return false;
    }
    if (lhs.doubleValue != rhs.doubleValue) {
        return false;
    }
    if (lhs.stringValue != rhs.stringValue) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Value& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .attributes = ";
    os += ::android::hardware::toString(o.attributes);
    os += ", .values = ";
    os += ::android::hardware::toString(o.values);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.attributes != rhs.attributes) {
        return false;
    }
    if (lhs.values != rhs.values) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::drm::V1_1::DrmMetricGroup& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".metrics = ";
    os += ::android::hardware::toString(o.metrics);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_1::DrmMetricGroup& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_1::DrmMetricGroup& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup& rhs) {
    if (lhs.metrics != rhs.metrics) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_1::DrmMetricGroup& lhs, const ::android::hardware::drm::V1_1::DrmMetricGroup& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::drm::V1_1::HdcpLevel>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_1::HdcpLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_1::HdcpLevel::HDCP_UNKNOWN) == static_cast<uint32_t>(::android::hardware::drm::V1_1::HdcpLevel::HDCP_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "HDCP_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::HdcpLevel::HDCP_UNKNOWN;
    }
    if ((o & ::android::hardware::drm::V1_1::HdcpLevel::HDCP_NONE) == static_cast<uint32_t>(::android::hardware::drm::V1_1::HdcpLevel::HDCP_NONE)) {
        os += (first ? "" : " | ");
        os += "HDCP_NONE";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::HdcpLevel::HDCP_NONE;
    }
    if ((o & ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V1) == static_cast<uint32_t>(::android::hardware::drm::V1_1::HdcpLevel::HDCP_V1)) {
        os += (first ? "" : " | ");
        os += "HDCP_V1";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V1;
    }
    if ((o & ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2) == static_cast<uint32_t>(::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2)) {
        os += (first ? "" : " | ");
        os += "HDCP_V2";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2;
    }
    if ((o & ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2_1) == static_cast<uint32_t>(::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2_1)) {
        os += (first ? "" : " | ");
        os += "HDCP_V2_1";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2_1;
    }
    if ((o & ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2_2) == static_cast<uint32_t>(::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2_2)) {
        os += (first ? "" : " | ");
        os += "HDCP_V2_2";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2_2;
    }
    if ((o & ::android::hardware::drm::V1_1::HdcpLevel::HDCP_NO_OUTPUT) == static_cast<uint32_t>(::android::hardware::drm::V1_1::HdcpLevel::HDCP_NO_OUTPUT)) {
        os += (first ? "" : " | ");
        os += "HDCP_NO_OUTPUT";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::HdcpLevel::HDCP_NO_OUTPUT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_1::HdcpLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_1::HdcpLevel::HDCP_UNKNOWN) {
        return "HDCP_UNKNOWN";
    }
    if (o == ::android::hardware::drm::V1_1::HdcpLevel::HDCP_NONE) {
        return "HDCP_NONE";
    }
    if (o == ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V1) {
        return "HDCP_V1";
    }
    if (o == ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2) {
        return "HDCP_V2";
    }
    if (o == ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2_1) {
        return "HDCP_V2_1";
    }
    if (o == ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2_2) {
        return "HDCP_V2_2";
    }
    if (o == ::android::hardware::drm::V1_1::HdcpLevel::HDCP_NO_OUTPUT) {
        return "HDCP_NO_OUTPUT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_1::HdcpLevel o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::drm::V1_1::KeyRequestType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_1::KeyRequestType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_1::KeyRequestType::INITIAL) == static_cast<uint32_t>(::android::hardware::drm::V1_1::KeyRequestType::INITIAL)) {
        os += (first ? "" : " | ");
        os += "INITIAL";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::KeyRequestType::INITIAL;
    }
    if ((o & ::android::hardware::drm::V1_1::KeyRequestType::RENEWAL) == static_cast<uint32_t>(::android::hardware::drm::V1_1::KeyRequestType::RENEWAL)) {
        os += (first ? "" : " | ");
        os += "RENEWAL";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::KeyRequestType::RENEWAL;
    }
    if ((o & ::android::hardware::drm::V1_1::KeyRequestType::RELEASE) == static_cast<uint32_t>(::android::hardware::drm::V1_1::KeyRequestType::RELEASE)) {
        os += (first ? "" : " | ");
        os += "RELEASE";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::KeyRequestType::RELEASE;
    }
    if ((o & ::android::hardware::drm::V1_1::KeyRequestType::UNKNOWN) == static_cast<uint32_t>(::android::hardware::drm::V1_1::KeyRequestType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::KeyRequestType::UNKNOWN;
    }
    if ((o & ::android::hardware::drm::V1_1::KeyRequestType::NONE) == static_cast<uint32_t>(::android::hardware::drm::V1_1::KeyRequestType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::KeyRequestType::NONE;
    }
    if ((o & ::android::hardware::drm::V1_1::KeyRequestType::UPDATE) == static_cast<uint32_t>(::android::hardware::drm::V1_1::KeyRequestType::UPDATE)) {
        os += (first ? "" : " | ");
        os += "UPDATE";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::KeyRequestType::UPDATE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_1::KeyRequestType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_1::KeyRequestType::INITIAL) {
        return "INITIAL";
    }
    if (o == ::android::hardware::drm::V1_1::KeyRequestType::RENEWAL) {
        return "RENEWAL";
    }
    if (o == ::android::hardware::drm::V1_1::KeyRequestType::RELEASE) {
        return "RELEASE";
    }
    if (o == ::android::hardware::drm::V1_1::KeyRequestType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::drm::V1_1::KeyRequestType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::drm::V1_1::KeyRequestType::UPDATE) {
        return "UPDATE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_1::KeyRequestType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::drm::V1_1::SecurityLevel>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_1::SecurityLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_1::SecurityLevel::UNKNOWN) == static_cast<uint32_t>(::android::hardware::drm::V1_1::SecurityLevel::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::SecurityLevel::UNKNOWN;
    }
    if ((o & ::android::hardware::drm::V1_1::SecurityLevel::SW_SECURE_CRYPTO) == static_cast<uint32_t>(::android::hardware::drm::V1_1::SecurityLevel::SW_SECURE_CRYPTO)) {
        os += (first ? "" : " | ");
        os += "SW_SECURE_CRYPTO";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::SecurityLevel::SW_SECURE_CRYPTO;
    }
    if ((o & ::android::hardware::drm::V1_1::SecurityLevel::SW_SECURE_DECODE) == static_cast<uint32_t>(::android::hardware::drm::V1_1::SecurityLevel::SW_SECURE_DECODE)) {
        os += (first ? "" : " | ");
        os += "SW_SECURE_DECODE";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::SecurityLevel::SW_SECURE_DECODE;
    }
    if ((o & ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_CRYPTO) == static_cast<uint32_t>(::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_CRYPTO)) {
        os += (first ? "" : " | ");
        os += "HW_SECURE_CRYPTO";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_CRYPTO;
    }
    if ((o & ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_DECODE) == static_cast<uint32_t>(::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_DECODE)) {
        os += (first ? "" : " | ");
        os += "HW_SECURE_DECODE";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_DECODE;
    }
    if ((o & ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_ALL) == static_cast<uint32_t>(::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_ALL)) {
        os += (first ? "" : " | ");
        os += "HW_SECURE_ALL";
        first = false;
        flipped |= ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_ALL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_1::SecurityLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_1::SecurityLevel::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::drm::V1_1::SecurityLevel::SW_SECURE_CRYPTO) {
        return "SW_SECURE_CRYPTO";
    }
    if (o == ::android::hardware::drm::V1_1::SecurityLevel::SW_SECURE_DECODE) {
        return "SW_SECURE_DECODE";
    }
    if (o == ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_CRYPTO) {
        return "HW_SECURE_CRYPTO";
    }
    if (o == ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_DECODE) {
        return "HW_SECURE_DECODE";
    }
    if (o == ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_ALL) {
        return "HW_SECURE_ALL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_1::SecurityLevel o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::drm::V1_1::SecureStopRelease& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".opaqueData = ";
    os += ::android::hardware::toString(o.opaqueData);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_1::SecureStopRelease& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_1::SecureStopRelease& lhs, const ::android::hardware::drm::V1_1::SecureStopRelease& rhs) {
    if (lhs.opaqueData != rhs.opaqueData) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_1::SecureStopRelease& lhs, const ::android::hardware::drm::V1_1::SecureStopRelease& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_1
}  // namespace drm
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
template<> inline constexpr std::array<::android::hardware::drm::V1_1::DrmMetricGroup::ValueType, 3> hidl_enum_values<::android::hardware::drm::V1_1::DrmMetricGroup::ValueType> = {
    ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::INT64_TYPE,
    ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::DOUBLE_TYPE,
    ::android::hardware::drm::V1_1::DrmMetricGroup::ValueType::STRING_TYPE,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_1::HdcpLevel, 7> hidl_enum_values<::android::hardware::drm::V1_1::HdcpLevel> = {
    ::android::hardware::drm::V1_1::HdcpLevel::HDCP_UNKNOWN,
    ::android::hardware::drm::V1_1::HdcpLevel::HDCP_NONE,
    ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V1,
    ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2,
    ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2_1,
    ::android::hardware::drm::V1_1::HdcpLevel::HDCP_V2_2,
    ::android::hardware::drm::V1_1::HdcpLevel::HDCP_NO_OUTPUT,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_1::KeyRequestType, 6> hidl_enum_values<::android::hardware::drm::V1_1::KeyRequestType> = {
    ::android::hardware::drm::V1_1::KeyRequestType::INITIAL,
    ::android::hardware::drm::V1_1::KeyRequestType::RENEWAL,
    ::android::hardware::drm::V1_1::KeyRequestType::RELEASE,
    ::android::hardware::drm::V1_1::KeyRequestType::UNKNOWN,
    ::android::hardware::drm::V1_1::KeyRequestType::NONE,
    ::android::hardware::drm::V1_1::KeyRequestType::UPDATE,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_1::SecurityLevel, 6> hidl_enum_values<::android::hardware::drm::V1_1::SecurityLevel> = {
    ::android::hardware::drm::V1_1::SecurityLevel::UNKNOWN,
    ::android::hardware::drm::V1_1::SecurityLevel::SW_SECURE_CRYPTO,
    ::android::hardware::drm::V1_1::SecurityLevel::SW_SECURE_DECODE,
    ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_CRYPTO,
    ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_DECODE,
    ::android::hardware::drm::V1_1::SecurityLevel::HW_SECURE_ALL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_1_TYPES_H
