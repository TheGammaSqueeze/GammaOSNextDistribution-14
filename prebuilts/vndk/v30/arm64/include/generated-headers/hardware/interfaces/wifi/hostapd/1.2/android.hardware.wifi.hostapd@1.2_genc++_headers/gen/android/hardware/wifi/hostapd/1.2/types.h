#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_2_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_2_TYPES_H

#include <android/hardware/wifi/hostapd/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_2 {

// Forward declaration for forward reference support:
enum class HostapdStatusCode : uint32_t;
enum class Ieee80211ReasonCode : uint16_t;
struct HostapdStatus;
enum class DebugLevel : uint32_t;

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
    /**
     * Failure because unknown the client.
     */
    FAILURE_CLIENT_UNKNOWN = 5u /* ::android::hardware::wifi::hostapd::V1_0::HostapdStatusCode.FAILURE_IFACE_EXISTS implicitly + 1 */,
};

/**
 * Enum values indicating the reason code for disconnect packet.
 * Reason codes (IEEE Std 802.11-2016, 9.4.1.7, Table 9-45).
 */
enum class Ieee80211ReasonCode : uint16_t {
    WLAN_REASON_UNSPECIFIED = 1,
    WLAN_REASON_PREV_AUTH_NOT_VALID = 2,
    WLAN_REASON_DISASSOC_AP_BUSY = 5,
};

/**
 * Mac Address type. 6 octets representing physical address of a device.
 */
typedef ::android::hardware::hidl_array<uint8_t, 6> MacAddress;

/**
 * Generic structure to return the status of any hostapd operation.
 */
struct HostapdStatus final {
    ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode code __attribute__ ((aligned(4)));
    /**
     * A vendor-specific error message to provide more information beyond the
     * status code.
     * This must be used for debugging purposes only.
     */
    ::android::hardware::hidl_string debugMessage __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::HostapdStatus, code) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::HostapdStatus, debugMessage) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::hostapd::V1_2::HostapdStatus) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::hostapd::V1_2::HostapdStatus) == 8, "wrong alignment");

/**
 * Debug levels for the hostapd.
 * Only log messages with a level greater than the set level
 * (via |setDebugParams|) will be logged.
 */
enum class DebugLevel : uint32_t {
    EXCESSIVE = 0u,
    MSGDUMP = 1u,
    DEBUG = 2u,
    INFO = 3u,
    WARNING = 4u,
    ERROR = 5u,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode o);
static inline void PrintTo(::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode lhs, const ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode lhs, const ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode o);
static inline void PrintTo(::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode lhs, const ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode lhs, const ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& o);
static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& lhs, const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& rhs);
static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& lhs, const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::hostapd::V1_2::DebugLevel o);
static inline void PrintTo(::android::hardware::wifi::hostapd::V1_2::DebugLevel o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::hostapd::V1_2::DebugLevel lhs, const ::android::hardware::wifi::hostapd::V1_2::DebugLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::hostapd::V1_2::DebugLevel rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::hostapd::V1_2::DebugLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::hostapd::V1_2::DebugLevel lhs, const ::android::hardware::wifi::hostapd::V1_2::DebugLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::hostapd::V1_2::DebugLevel rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::hostapd::V1_2::DebugLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::hostapd::V1_2::DebugLevel e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::hostapd::V1_2::DebugLevel e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::SUCCESS;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_UNKNOWN;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_ARGS_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_ARGS_INVALID)) {
        os += (first ? "" : " | ");
        os += "FAILURE_ARGS_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_ARGS_INVALID;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_IFACE_UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_IFACE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_IFACE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_IFACE_UNKNOWN;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_IFACE_EXISTS) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_IFACE_EXISTS)) {
        os += (first ? "" : " | ");
        os += "FAILURE_IFACE_EXISTS";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_IFACE_EXISTS;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_CLIENT_UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_CLIENT_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_CLIENT_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_CLIENT_UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_UNKNOWN) {
        return "FAILURE_UNKNOWN";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_ARGS_INVALID) {
        return "FAILURE_ARGS_INVALID";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_IFACE_UNKNOWN) {
        return "FAILURE_IFACE_UNKNOWN";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_IFACE_EXISTS) {
        return "FAILURE_IFACE_EXISTS";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_CLIENT_UNKNOWN) {
        return "FAILURE_CLIENT_UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_UNSPECIFIED) == static_cast<uint16_t>(::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_UNSPECIFIED)) {
        os += (first ? "" : " | ");
        os += "WLAN_REASON_UNSPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_UNSPECIFIED;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_PREV_AUTH_NOT_VALID) == static_cast<uint16_t>(::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_PREV_AUTH_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "WLAN_REASON_PREV_AUTH_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_PREV_AUTH_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_DISASSOC_AP_BUSY) == static_cast<uint16_t>(::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_DISASSOC_AP_BUSY)) {
        os += (first ? "" : " | ");
        os += "WLAN_REASON_DISASSOC_AP_BUSY";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_DISASSOC_AP_BUSY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_UNSPECIFIED) {
        return "WLAN_REASON_UNSPECIFIED";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_PREV_AUTH_NOT_VALID) {
        return "WLAN_REASON_PREV_AUTH_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_DISASSOC_AP_BUSY) {
        return "WLAN_REASON_DISASSOC_AP_BUSY";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".code = ";
    os += ::android::hardware::wifi::hostapd::V1_2::toString(o.code);
    os += ", .debugMessage = ";
    os += ::android::hardware::toString(o.debugMessage);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& lhs, const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& rhs) {
    if (lhs.code != rhs.code) {
        return false;
    }
    if (lhs.debugMessage != rhs.debugMessage) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& lhs, const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::hostapd::V1_2::DebugLevel>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::hostapd::V1_2::DebugLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::hostapd::V1_2::DebugLevel::EXCESSIVE) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::DebugLevel::EXCESSIVE)) {
        os += (first ? "" : " | ");
        os += "EXCESSIVE";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::DebugLevel::EXCESSIVE;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::DebugLevel::MSGDUMP) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::DebugLevel::MSGDUMP)) {
        os += (first ? "" : " | ");
        os += "MSGDUMP";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::DebugLevel::MSGDUMP;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::DebugLevel::DEBUG) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::DebugLevel::DEBUG)) {
        os += (first ? "" : " | ");
        os += "DEBUG";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::DebugLevel::DEBUG;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::DebugLevel::INFO) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::DebugLevel::INFO)) {
        os += (first ? "" : " | ");
        os += "INFO";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::DebugLevel::INFO;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::DebugLevel::WARNING) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::DebugLevel::WARNING)) {
        os += (first ? "" : " | ");
        os += "WARNING";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::DebugLevel::WARNING;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::DebugLevel::ERROR) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::DebugLevel::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::DebugLevel::ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::hostapd::V1_2::DebugLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::hostapd::V1_2::DebugLevel::EXCESSIVE) {
        return "EXCESSIVE";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::DebugLevel::MSGDUMP) {
        return "MSGDUMP";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::DebugLevel::DEBUG) {
        return "DEBUG";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::DebugLevel::INFO) {
        return "INFO";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::DebugLevel::WARNING) {
        return "WARNING";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::DebugLevel::ERROR) {
        return "ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::hostapd::V1_2::DebugLevel o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_2
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
template<> inline constexpr std::array<::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode, 6> hidl_enum_values<::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode> = {
    ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::SUCCESS,
    ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_UNKNOWN,
    ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_ARGS_INVALID,
    ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_IFACE_UNKNOWN,
    ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_IFACE_EXISTS,
    ::android::hardware::wifi::hostapd::V1_2::HostapdStatusCode::FAILURE_CLIENT_UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode, 3> hidl_enum_values<::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode> = {
    ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_UNSPECIFIED,
    ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_PREV_AUTH_NOT_VALID,
    ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode::WLAN_REASON_DISASSOC_AP_BUSY,
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
template<> inline constexpr std::array<::android::hardware::wifi::hostapd::V1_2::DebugLevel, 6> hidl_enum_values<::android::hardware::wifi::hostapd::V1_2::DebugLevel> = {
    ::android::hardware::wifi::hostapd::V1_2::DebugLevel::EXCESSIVE,
    ::android::hardware::wifi::hostapd::V1_2::DebugLevel::MSGDUMP,
    ::android::hardware::wifi::hostapd::V1_2::DebugLevel::DEBUG,
    ::android::hardware::wifi::hostapd::V1_2::DebugLevel::INFO,
    ::android::hardware::wifi::hostapd::V1_2::DebugLevel::WARNING,
    ::android::hardware::wifi::hostapd::V1_2::DebugLevel::ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_2_TYPES_H
