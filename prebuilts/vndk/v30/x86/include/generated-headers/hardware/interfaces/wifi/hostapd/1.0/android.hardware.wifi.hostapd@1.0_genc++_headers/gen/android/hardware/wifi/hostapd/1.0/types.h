#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class HostapdStatusCode : uint32_t;
struct HostapdStatus;

/**
 * Enum values indicating the status of any hostapd operation.
 */
enum class HostapdStatusCode : uint32_t {
    /**
     * No errors.
     */
    SUCCESS = 0u,
    /**
     * Unknown failure occured.
     */
    FAILURE_UNKNOWN = 1u /* ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode.SUCCESS implicitly + 1 */,
    /**
     * One or more of the incoming args is invalid.
     */
    FAILURE_ARGS_INVALID = 2u /* ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode.FAILURE_UNKNOWN implicitly + 1 */,
    /**
     * Iface with the provided name does not exist.
     */
    FAILURE_IFACE_UNKNOWN = 3u /* ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode.FAILURE_ARGS_INVALID implicitly + 1 */,
    /**
     * Iface with the provided name already exists.
     */
    FAILURE_IFACE_EXISTS = 4u /* ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode.FAILURE_IFACE_UNKNOWN implicitly + 1 */,
};

/**
 * Generic structure to return the status of any hostapd operation.
 */
struct HostapdStatus final {
    ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode code __attribute__ ((aligned(4)));
    /**
     * A vendor-specific error message to provide more information beyond the
     * status code.
     * This must be used for debugging purposes only.
     */
    ::android::hardware::hidl_string debugMessage __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::hostapd::V1_0::HostapdStatus, code) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::hostapd::V1_0::HostapdStatus, debugMessage) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::hostapd::V1_0::HostapdStatus) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::hostapd::V1_0::HostapdStatus) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode o);
static inline void PrintTo(::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode lhs, const ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode lhs, const ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& o);
static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& lhs, const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& rhs);
static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& lhs, const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::SUCCESS;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_UNKNOWN;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_ARGS_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_ARGS_INVALID)) {
        os += (first ? "" : " | ");
        os += "FAILURE_ARGS_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_ARGS_INVALID;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_IFACE_UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_IFACE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_IFACE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_IFACE_UNKNOWN;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_IFACE_EXISTS) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_IFACE_EXISTS)) {
        os += (first ? "" : " | ");
        os += "FAILURE_IFACE_EXISTS";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_IFACE_EXISTS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_UNKNOWN) {
        return "FAILURE_UNKNOWN";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_ARGS_INVALID) {
        return "FAILURE_ARGS_INVALID";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_IFACE_UNKNOWN) {
        return "FAILURE_IFACE_UNKNOWN";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_IFACE_EXISTS) {
        return "FAILURE_IFACE_EXISTS";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".code = ";
    os += ::android::hardware::wifi::hostapd::V1_0::toString(o.code);
    os += ", .debugMessage = ";
    os += ::android::hardware::toString(o.debugMessage);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& lhs, const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& rhs) {
    if (lhs.code != rhs.code) {
        return false;
    }
    if (lhs.debugMessage != rhs.debugMessage) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& lhs, const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace hostapd
}  // namespace wifi
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
template<> inline constexpr std::array<::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode, 5> hidl_enum_values<::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode> = {
    ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::SUCCESS,
    ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_UNKNOWN,
    ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_ARGS_INVALID,
    ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_IFACE_UNKNOWN,
    ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode::FAILURE_IFACE_EXISTS,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_0_TYPES_H
