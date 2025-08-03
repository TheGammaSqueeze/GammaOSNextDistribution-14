#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace cas {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;
struct HidlCasPluginDescriptor;

enum class Status : uint32_t {
    /**
     * The CAS plugin must return OK when an operation completes without any
     * errors.
     */
    OK = 0u,
    /**
     * The CAS plugin must return ERROR_CAS_NO_LICENSE, when descrambling is
     * attempted and no license keys have been provided.
     */
    ERROR_CAS_NO_LICENSE = 1u /* ::android::hardware::cas::V1_0::Status.OK implicitly + 1 */,
    /**
     * ERROR_CAS_LICENSE_EXPIRED must be returned when an attempt is made
     * to use a license and the keys in that license have expired.
     */
    ERROR_CAS_LICENSE_EXPIRED = 2u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_NO_LICENSE implicitly + 1 */,
    /**
     * The CAS plugin must return ERROR_CAS_SESSION_NOT_OPENED when an
     * attempt is made to use a session that has not been opened.
     */
    ERROR_CAS_SESSION_NOT_OPENED = 3u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_LICENSE_EXPIRED implicitly + 1 */,
    /**
     * The CAS plugin must return ERROR_CAS_CANNOT_HANDLE when an unsupported
     * data format or operation is attempted.
     */
    ERROR_CAS_CANNOT_HANDLE = 4u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_SESSION_NOT_OPENED implicitly + 1 */,
    /**
     * ERROR_CAS_INVALID_STATE must be returned when the device is in a state
     * where it is not able to perform descrambling.
     */
    ERROR_CAS_INVALID_STATE = 5u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_CANNOT_HANDLE implicitly + 1 */,
    /**
     * The CAS plugin must return BAD_VALUE whenever an illegal parameter is
     * passed to one of the interface functions.
     */
    BAD_VALUE = 6u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_INVALID_STATE implicitly + 1 */,
    /**
     * The CAS plugin must return ERROR_CAS_NOT_PROVISIONED when the device
     * has not yet been provisioned.
     */
    ERROR_CAS_NOT_PROVISIONED = 7u /* ::android::hardware::cas::V1_0::Status.BAD_VALUE implicitly + 1 */,
    /**
     * ERROR_CAS_RESOURCE_BUSY must be returned when resources, such as CAS
     * sessions or secure buffers are not available to perform a requested
     * operation because they are already in use.
     */
    ERROR_CAS_RESOURCE_BUSY = 8u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_NOT_PROVISIONED implicitly + 1 */,
    /**
     * The CAS Plugin must return ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION
     * when the output protection level enabled on the device is not
     * sufficient to meet the requirements in the license policy. HDCP is an
     * example of a form of output protection.
     */
    ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION = 9u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_RESOURCE_BUSY implicitly + 1 */,
    /**
     * The CAS Plugin must return ERROR_CAS_TAMPER_DETECTED if an attempt to
     * tamper with the CAS system is detected.
     */
    ERROR_CAS_TAMPER_DETECTED = 10u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION implicitly + 1 */,
    /**
     * The CAS Plugin must return ERROR_CAS_DEVICE_REVOKED if the response
     * indicates that the device has been revoked. Device revocation means
     * that the device is no longer permitted to play content.
     */
    ERROR_CAS_DEVICE_REVOKED = 11u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_TAMPER_DETECTED implicitly + 1 */,
    /**
     * The CAS plugin must return ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED when
     * descrambling is failing because the session is not initialized properly.
     */
    ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED = 12u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_DEVICE_REVOKED implicitly + 1 */,
    /**
     * The CAS Plugin must return ERROR_CAS_DECRYPT if the DescramblerPlugin's
     * descramble operation fails.
     */
    ERROR_CAS_DECRYPT = 13u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED implicitly + 1 */,
    /**
     * ERROR_CAS_UNKNOWN must be returned when a fatal failure occurs and no
     * other defined error is appropriate.
     */
    ERROR_CAS_UNKNOWN = 14u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_DECRYPT implicitly + 1 */,
};

typedef ::android::hardware::hidl_vec<uint8_t> HidlCasSessionId;

typedef ::android::hardware::hidl_vec<uint8_t> HidlCasData;

/**
 * Describes a CAS plugin with its system id and name.
 */
struct HidlCasPluginDescriptor final {
    int32_t caSystemId __attribute__ ((aligned(4)));
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::cas::V1_0::HidlCasPluginDescriptor, caSystemId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::cas::V1_0::HidlCasPluginDescriptor, name) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::cas::V1_0::HidlCasPluginDescriptor) == 24, "wrong size");
static_assert(__alignof(::android::hardware::cas::V1_0::HidlCasPluginDescriptor) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::cas::V1_0::Status o);
static inline void PrintTo(::android::hardware::cas::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::cas::V1_0::Status lhs, const ::android::hardware::cas::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::cas::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::cas::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::cas::V1_0::Status lhs, const ::android::hardware::cas::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::cas::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::cas::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::cas::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::cas::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& o);
static inline void PrintTo(const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& lhs, const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& rhs);
static inline bool operator!=(const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& lhs, const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::cas::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::cas::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::cas::V1_0::Status::OK) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::OK;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_NO_LICENSE) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_NO_LICENSE)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_NO_LICENSE";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_NO_LICENSE;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_LICENSE_EXPIRED) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_LICENSE_EXPIRED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_LICENSE_EXPIRED";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_LICENSE_EXPIRED;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_SESSION_NOT_OPENED) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_SESSION_NOT_OPENED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_SESSION_NOT_OPENED";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_SESSION_NOT_OPENED;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_CANNOT_HANDLE) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_CANNOT_HANDLE)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_CANNOT_HANDLE";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_CANNOT_HANDLE;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_INVALID_STATE) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_INVALID_STATE;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::BAD_VALUE) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "BAD_VALUE";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::BAD_VALUE;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_NOT_PROVISIONED) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_NOT_PROVISIONED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_NOT_PROVISIONED";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_NOT_PROVISIONED;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_RESOURCE_BUSY) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_RESOURCE_BUSY)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_RESOURCE_BUSY";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_RESOURCE_BUSY;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_TAMPER_DETECTED) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_TAMPER_DETECTED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_TAMPER_DETECTED";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_TAMPER_DETECTED;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_DEVICE_REVOKED) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_DEVICE_REVOKED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_DEVICE_REVOKED";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_DEVICE_REVOKED;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_DECRYPT) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_DECRYPT)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_DECRYPT";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_DECRYPT;
    }
    if ((o & ::android::hardware::cas::V1_0::Status::ERROR_CAS_UNKNOWN) == static_cast<uint32_t>(::android::hardware::cas::V1_0::Status::ERROR_CAS_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::cas::V1_0::Status::ERROR_CAS_UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::cas::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::cas::V1_0::Status::OK) {
        return "OK";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_NO_LICENSE) {
        return "ERROR_CAS_NO_LICENSE";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_LICENSE_EXPIRED) {
        return "ERROR_CAS_LICENSE_EXPIRED";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_SESSION_NOT_OPENED) {
        return "ERROR_CAS_SESSION_NOT_OPENED";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_CANNOT_HANDLE) {
        return "ERROR_CAS_CANNOT_HANDLE";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_INVALID_STATE) {
        return "ERROR_CAS_INVALID_STATE";
    }
    if (o == ::android::hardware::cas::V1_0::Status::BAD_VALUE) {
        return "BAD_VALUE";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_NOT_PROVISIONED) {
        return "ERROR_CAS_NOT_PROVISIONED";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_RESOURCE_BUSY) {
        return "ERROR_CAS_RESOURCE_BUSY";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION) {
        return "ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_TAMPER_DETECTED) {
        return "ERROR_CAS_TAMPER_DETECTED";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_DEVICE_REVOKED) {
        return "ERROR_CAS_DEVICE_REVOKED";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED) {
        return "ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_DECRYPT) {
        return "ERROR_CAS_DECRYPT";
    }
    if (o == ::android::hardware::cas::V1_0::Status::ERROR_CAS_UNKNOWN) {
        return "ERROR_CAS_UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::cas::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".caSystemId = ";
    os += ::android::hardware::toString(o.caSystemId);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& lhs, const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& rhs) {
    if (lhs.caSystemId != rhs.caSystemId) {
        return false;
    }
    if (lhs.name != rhs.name) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& lhs, const ::android::hardware::cas::V1_0::HidlCasPluginDescriptor& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace cas
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
template<> inline constexpr std::array<::android::hardware::cas::V1_0::Status, 15> hidl_enum_values<::android::hardware::cas::V1_0::Status> = {
    ::android::hardware::cas::V1_0::Status::OK,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_NO_LICENSE,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_LICENSE_EXPIRED,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_SESSION_NOT_OPENED,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_CANNOT_HANDLE,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_INVALID_STATE,
    ::android::hardware::cas::V1_0::Status::BAD_VALUE,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_NOT_PROVISIONED,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_RESOURCE_BUSY,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_TAMPER_DETECTED,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_DEVICE_REVOKED,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_DECRYPT,
    ::android::hardware::cas::V1_0::Status::ERROR_CAS_UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_TYPES_H
