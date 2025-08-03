#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class SupplicantStatusCode : uint32_t;
struct SupplicantStatus;
enum class IfaceType : uint32_t;
enum class P2pGroupCapabilityMask : uint32_t;
enum class WpsConfigMethods : uint16_t;

/**
 * Enum values indicating the status of any supplicant operation.
 */
enum class SupplicantStatusCode : uint32_t {
    /**
     * No errors.
     */
    SUCCESS = 0u,
    /**
     * Unknown failure occured.
     */
    FAILURE_UNKNOWN = 1u /* ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode.SUCCESS implicitly + 1 */,
    /**
     * One of the incoming args is invalid.
     */
    FAILURE_ARGS_INVALID = 2u /* ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode.FAILURE_UNKNOWN implicitly + 1 */,
    /**
     * |ISupplicantIface| HIDL interface object is no longer valid.
     */
    FAILURE_IFACE_INVALID = 3u /* ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode.FAILURE_ARGS_INVALID implicitly + 1 */,
    /**
     * Iface with the provided name does not exist.
     */
    FAILURE_IFACE_UNKNOWN = 4u /* ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode.FAILURE_IFACE_INVALID implicitly + 1 */,
    /**
     * Iface with the provided name already exists.
     */
    FAILURE_IFACE_EXISTS = 5u /* ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode.FAILURE_IFACE_UNKNOWN implicitly + 1 */,
    /**
     * Iface is disabled and cannot be used.
     */
    FAILURE_IFACE_DISABLED = 6u /* ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode.FAILURE_IFACE_EXISTS implicitly + 1 */,
    /**
     * Iface is not currently disconnected, so cannot reconnect.
     */
    FAILURE_IFACE_NOT_DISCONNECTED = 7u /* ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode.FAILURE_IFACE_DISABLED implicitly + 1 */,
    /**
     * |ISupplicantNetwork| HIDL interface object is no longer valid.
     */
    FAILURE_NETWORK_INVALID = 8u /* ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode.FAILURE_IFACE_NOT_DISCONNECTED implicitly + 1 */,
    /**
     * Network with the provided id does not exist.
     */
    FAILURE_NETWORK_UNKNOWN = 9u /* ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode.FAILURE_NETWORK_INVALID implicitly + 1 */,
};

/**
 * Generic structure to return the status of any supplicant operation.
 */
struct SupplicantStatus final {
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode code __attribute__ ((aligned(4)));
    /**
     * A vendor specific error message to provide more information beyond the
     * status code.
     * This will be used for debbuging purposes only.
     */
    ::android::hardware::hidl_string debugMessage __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::SupplicantStatus, code) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::SupplicantStatus, debugMessage) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::supplicant::V1_0::SupplicantStatus) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::supplicant::V1_0::SupplicantStatus) == 8, "wrong alignment");

/**
 * SSID type. Max of 32 octets representing service identifier of a network.
 */
typedef ::android::hardware::hidl_vec<uint8_t> Ssid;

/**
 * MAC Address type. 6 octets representing physical address of a device.
 */
typedef ::android::hardware::hidl_array<uint8_t, 6> MacAddress;

/**
 * BSSID type. 6 octets representing the physical address of an AP.
 */
typedef ::android::hardware::hidl_array<uint8_t, 6> Bssid;

/**
 * Supplicant network ID type.
 */
typedef uint32_t SupplicantNetworkId;

/**
 * List of Iface types supported.
 */
enum class IfaceType : uint32_t {
    STA = 0u,
    P2P = 1u /* ::android::hardware::wifi::supplicant::V1_0::IfaceType.STA implicitly + 1 */,
};

/**
 * P2P group capability.
 */
enum class P2pGroupCapabilityMask : uint32_t {
    GROUP_OWNER = 1u /* 1 << 0 */,
    PERSISTENT_GROUP = 2u /* 1 << 1 */,
    GROUP_LIMIT = 4u /* 1 << 2 */,
    INTRA_BSS_DIST = 8u /* 1 << 3 */,
    CROSS_CONN = 16u /* 1 << 4 */,
    PERSISTENT_RECONN = 32u /* 1 << 5 */,
    GROUP_FORMATION = 64u /* 1 << 6 */,
};

/**
 * WPS config methods.
 * Refer to section 3 of IBSS with Wi-Fi Protected Setup
 * Technical Specification Version 1.0.0.
 */
enum class WpsConfigMethods : uint16_t {
    USBA = 1 /* 0x0001 */,
    ETHERNET = 2 /* 0x0002 */,
    LABEL = 4 /* 0x0004 */,
    DISPLAY = 8 /* 0x0008 */,
    EXT_NFC_TOKEN = 16 /* 0x0010 */,
    INT_NFC_TOKEN = 32 /* 0x0020 */,
    NFC_INTERFACE = 64 /* 0x0040 */,
    PUSHBUTTON = 128 /* 0x0080 */,
    KEYPAD = 256 /* 0x0100 */,
    VIRT_PUSHBUTTON = 640 /* 0x0280 */,
    PHY_PUSHBUTTON = 1152 /* 0x0480 */,
    P2PS = 4096 /* 0x1000 */,
    VIRT_DISPLAY = 8200 /* 0x2008 */,
    PHY_DISPLAY = 16392 /* 0x4008 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode lhs, const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode lhs, const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& o);
static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& lhs, const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& rhs);
static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& lhs, const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::IfaceType o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::IfaceType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::IfaceType lhs, const ::android::hardware::wifi::supplicant::V1_0::IfaceType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::IfaceType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::IfaceType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::IfaceType lhs, const ::android::hardware::wifi::supplicant::V1_0::IfaceType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::IfaceType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::IfaceType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::IfaceType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::IfaceType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask lhs, const ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask lhs, const ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods lhs, const ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods lhs, const ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::SUCCESS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_UNKNOWN;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_ARGS_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_ARGS_INVALID)) {
        os += (first ? "" : " | ");
        os += "FAILURE_ARGS_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_ARGS_INVALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_INVALID)) {
        os += (first ? "" : " | ");
        os += "FAILURE_IFACE_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_INVALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_IFACE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_UNKNOWN;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_EXISTS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_EXISTS)) {
        os += (first ? "" : " | ");
        os += "FAILURE_IFACE_EXISTS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_EXISTS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_DISABLED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_DISABLED)) {
        os += (first ? "" : " | ");
        os += "FAILURE_IFACE_DISABLED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_DISABLED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_NOT_DISCONNECTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_NOT_DISCONNECTED)) {
        os += (first ? "" : " | ");
        os += "FAILURE_IFACE_NOT_DISCONNECTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_NOT_DISCONNECTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_NETWORK_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_NETWORK_INVALID)) {
        os += (first ? "" : " | ");
        os += "FAILURE_NETWORK_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_NETWORK_INVALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_NETWORK_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_UNKNOWN) {
        return "FAILURE_UNKNOWN";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_ARGS_INVALID) {
        return "FAILURE_ARGS_INVALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_INVALID) {
        return "FAILURE_IFACE_INVALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_UNKNOWN) {
        return "FAILURE_IFACE_UNKNOWN";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_EXISTS) {
        return "FAILURE_IFACE_EXISTS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_DISABLED) {
        return "FAILURE_IFACE_DISABLED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_NOT_DISCONNECTED) {
        return "FAILURE_IFACE_NOT_DISCONNECTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_NETWORK_INVALID) {
        return "FAILURE_NETWORK_INVALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN) {
        return "FAILURE_NETWORK_UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".code = ";
    os += ::android::hardware::wifi::supplicant::V1_0::toString(o.code);
    os += ", .debugMessage = ";
    os += ::android::hardware::toString(o.debugMessage);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& lhs, const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& rhs) {
    if (lhs.code != rhs.code) {
        return false;
    }
    if (lhs.debugMessage != rhs.debugMessage) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& lhs, const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::IfaceType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::IfaceType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::IfaceType::STA) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::IfaceType::STA)) {
        os += (first ? "" : " | ");
        os += "STA";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::IfaceType::STA;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::IfaceType::P2P) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::IfaceType::P2P)) {
        os += (first ? "" : " | ");
        os += "P2P";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::IfaceType::P2P;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::IfaceType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::IfaceType::STA) {
        return "STA";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::IfaceType::P2P) {
        return "P2P";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::IfaceType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_OWNER) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_OWNER)) {
        os += (first ? "" : " | ");
        os += "GROUP_OWNER";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_OWNER;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::PERSISTENT_GROUP) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::PERSISTENT_GROUP)) {
        os += (first ? "" : " | ");
        os += "PERSISTENT_GROUP";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::PERSISTENT_GROUP;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_LIMIT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_LIMIT)) {
        os += (first ? "" : " | ");
        os += "GROUP_LIMIT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_LIMIT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::INTRA_BSS_DIST) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::INTRA_BSS_DIST)) {
        os += (first ? "" : " | ");
        os += "INTRA_BSS_DIST";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::INTRA_BSS_DIST;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::CROSS_CONN) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::CROSS_CONN)) {
        os += (first ? "" : " | ");
        os += "CROSS_CONN";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::CROSS_CONN;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::PERSISTENT_RECONN) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::PERSISTENT_RECONN)) {
        os += (first ? "" : " | ");
        os += "PERSISTENT_RECONN";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::PERSISTENT_RECONN;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_FORMATION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_FORMATION)) {
        os += (first ? "" : " | ");
        os += "GROUP_FORMATION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_FORMATION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_OWNER) {
        return "GROUP_OWNER";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::PERSISTENT_GROUP) {
        return "PERSISTENT_GROUP";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_LIMIT) {
        return "GROUP_LIMIT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::INTRA_BSS_DIST) {
        return "INTRA_BSS_DIST";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::CROSS_CONN) {
        return "CROSS_CONN";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::PERSISTENT_RECONN) {
        return "PERSISTENT_RECONN";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_FORMATION) {
        return "GROUP_FORMATION";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::USBA) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::USBA)) {
        os += (first ? "" : " | ");
        os += "USBA";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::USBA;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::ETHERNET) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::ETHERNET)) {
        os += (first ? "" : " | ");
        os += "ETHERNET";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::ETHERNET;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::LABEL) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::LABEL)) {
        os += (first ? "" : " | ");
        os += "LABEL";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::LABEL;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::DISPLAY) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::DISPLAY)) {
        os += (first ? "" : " | ");
        os += "DISPLAY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::DISPLAY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::EXT_NFC_TOKEN) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::EXT_NFC_TOKEN)) {
        os += (first ? "" : " | ");
        os += "EXT_NFC_TOKEN";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::EXT_NFC_TOKEN;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::INT_NFC_TOKEN) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::INT_NFC_TOKEN)) {
        os += (first ? "" : " | ");
        os += "INT_NFC_TOKEN";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::INT_NFC_TOKEN;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::NFC_INTERFACE) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::NFC_INTERFACE)) {
        os += (first ? "" : " | ");
        os += "NFC_INTERFACE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::NFC_INTERFACE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PUSHBUTTON) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PUSHBUTTON)) {
        os += (first ? "" : " | ");
        os += "PUSHBUTTON";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PUSHBUTTON;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::KEYPAD) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::KEYPAD)) {
        os += (first ? "" : " | ");
        os += "KEYPAD";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::KEYPAD;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::VIRT_PUSHBUTTON) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::VIRT_PUSHBUTTON)) {
        os += (first ? "" : " | ");
        os += "VIRT_PUSHBUTTON";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::VIRT_PUSHBUTTON;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PHY_PUSHBUTTON) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PHY_PUSHBUTTON)) {
        os += (first ? "" : " | ");
        os += "PHY_PUSHBUTTON";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PHY_PUSHBUTTON;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::P2PS) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::P2PS)) {
        os += (first ? "" : " | ");
        os += "P2PS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::P2PS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::VIRT_DISPLAY) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::VIRT_DISPLAY)) {
        os += (first ? "" : " | ");
        os += "VIRT_DISPLAY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::VIRT_DISPLAY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PHY_DISPLAY) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PHY_DISPLAY)) {
        os += (first ? "" : " | ");
        os += "PHY_DISPLAY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PHY_DISPLAY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::USBA) {
        return "USBA";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::ETHERNET) {
        return "ETHERNET";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::LABEL) {
        return "LABEL";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::DISPLAY) {
        return "DISPLAY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::EXT_NFC_TOKEN) {
        return "EXT_NFC_TOKEN";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::INT_NFC_TOKEN) {
        return "INT_NFC_TOKEN";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::NFC_INTERFACE) {
        return "NFC_INTERFACE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PUSHBUTTON) {
        return "PUSHBUTTON";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::KEYPAD) {
        return "KEYPAD";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::VIRT_PUSHBUTTON) {
        return "VIRT_PUSHBUTTON";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PHY_PUSHBUTTON) {
        return "PHY_PUSHBUTTON";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::P2PS) {
        return "P2PS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::VIRT_DISPLAY) {
        return "VIRT_DISPLAY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PHY_DISPLAY) {
        return "PHY_DISPLAY";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace supplicant
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode, 10> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode> = {
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::SUCCESS,
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_UNKNOWN,
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_ARGS_INVALID,
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_INVALID,
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_UNKNOWN,
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_EXISTS,
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_DISABLED,
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_IFACE_NOT_DISCONNECTED,
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_NETWORK_INVALID,
    ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::IfaceType, 2> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::IfaceType> = {
    ::android::hardware::wifi::supplicant::V1_0::IfaceType::STA,
    ::android::hardware::wifi::supplicant::V1_0::IfaceType::P2P,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask, 7> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask> = {
    ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_OWNER,
    ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::PERSISTENT_GROUP,
    ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_LIMIT,
    ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::INTRA_BSS_DIST,
    ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::CROSS_CONN,
    ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::PERSISTENT_RECONN,
    ::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask::GROUP_FORMATION,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods, 14> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> = {
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::USBA,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::ETHERNET,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::LABEL,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::DISPLAY,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::EXT_NFC_TOKEN,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::INT_NFC_TOKEN,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::NFC_INTERFACE,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PUSHBUTTON,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::KEYPAD,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::VIRT_PUSHBUTTON,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PHY_PUSHBUTTON,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::P2PS,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::VIRT_DISPLAY,
    ::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods::PHY_DISPLAY,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_TYPES_H
