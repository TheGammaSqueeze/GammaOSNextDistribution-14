#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_TYPES_H

#include <android/hardware/wifi/supplicant/1.2/types.h>
#include <android/hardware/wifi/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_3 {

// Forward declaration for forward reference support:
enum class OcspType : uint32_t;
enum class WifiTechnology : uint32_t;
struct ConnectionCapabilities;
enum class WpaDriverCapabilitiesMask : uint32_t;
enum class DppProgressCode : uint32_t;
enum class DppSuccessCode : uint32_t;
enum class DppFailureCode : uint32_t;

/**
 * OcspType: The type of OCSP request.
 */
enum class OcspType : uint32_t {
    NONE = 0u,
    REQUEST_CERT_STATUS = 1u /* ::android::hardware::wifi::supplicant::V1_3::OcspType.NONE implicitly + 1 */,
    REQUIRE_CERT_STATUS = 2u /* ::android::hardware::wifi::supplicant::V1_3::OcspType.REQUEST_CERT_STATUS implicitly + 1 */,
    REQUIRE_ALL_CERTS_STATUS = 3u /* ::android::hardware::wifi::supplicant::V1_3::OcspType.REQUIRE_CERT_STATUS implicitly + 1 */,
};

/**
 * Wifi Technologies
 */
enum class WifiTechnology : uint32_t {
    UNKNOWN = 0u,
    /**
     * For 802.11a/b/g
     */
    LEGACY = 1u,
    /**
     * For 802.11n
     */
    HT = 2u,
    /**
     * For 802.11ac
     */
    VHT = 3u,
    /**
     * For 802.11ax
     */
    HE = 4u,
};

/**
 * Connection Capabilities supported by current network and device
 */
struct ConnectionCapabilities final {
    /**
     * Wifi Technology
     */
    ::android::hardware::wifi::supplicant::V1_3::WifiTechnology technology __attribute__ ((aligned(4)));
    /**
     * channel bandwidth
     */
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz channelBandwidth __attribute__ ((aligned(4)));
    /**
     * max number of Tx spatial streams
     */
    uint32_t maxNumberTxSpatialStreams __attribute__ ((aligned(4)));
    /**
     * max number of Rx spatial streams
     */
    uint32_t maxNumberRxSpatialStreams __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities, technology) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities, channelBandwidth) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities, maxNumberTxSpatialStreams) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities, maxNumberRxSpatialStreams) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities) == 16, "wrong size");
static_assert(__alignof(::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities) == 4, "wrong alignment");

/**
 * WPA Driver capability.
 */
enum class WpaDriverCapabilitiesMask : uint32_t {
    /**
     * Multi Band Operation.
     */
    MBO = 1u /* 1 << 0 */,
    /**
     * Optimized Connectivity Experience.
     */
    OCE = 2u /* 1 << 1 */,
};

/**
 * DppProgressCode: Progress codes for DPP (Easy Connect)
 */
enum class DppProgressCode : uint32_t {
    AUTHENTICATION_SUCCESS = 0u,
    RESPONSE_PENDING = 1u /* ::android::hardware::wifi::supplicant::V1_2::DppProgressCode.AUTHENTICATION_SUCCESS implicitly + 1 */,
    CONFIGURATION_SENT_WAITING_RESPONSE = 2u /* ::android::hardware::wifi::supplicant::V1_2::DppProgressCode.RESPONSE_PENDING implicitly + 1 */,
    CONFIGURATION_ACCEPTED = 3u /* ::android::hardware::wifi::supplicant::V1_3::DppProgressCode.CONFIGURATION_SENT_WAITING_RESPONSE implicitly + 1 */,
};

/**
 * DppSuccessCode: Success codes for DPP (Easy Connect) Configurator
 */
enum class DppSuccessCode : uint32_t {
    /*
     * Replaces @1.2::onDppSuccessConfigSent()
     */
    CONFIGURATION_SENT = 0u,
    CONFIGURATION_APPLIED = 1u /* ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode.CONFIGURATION_SENT implicitly + 1 */,
};

/**
 * DppFailureCode: Error codes for DPP (Easy Connect)
 */
enum class DppFailureCode : uint32_t {
    INVALID_URI = 0u,
    AUTHENTICATION = 1u /* ::android::hardware::wifi::supplicant::V1_2::DppFailureCode.INVALID_URI implicitly + 1 */,
    NOT_COMPATIBLE = 2u /* ::android::hardware::wifi::supplicant::V1_2::DppFailureCode.AUTHENTICATION implicitly + 1 */,
    CONFIGURATION = 3u /* ::android::hardware::wifi::supplicant::V1_2::DppFailureCode.NOT_COMPATIBLE implicitly + 1 */,
    BUSY = 4u /* ::android::hardware::wifi::supplicant::V1_2::DppFailureCode.CONFIGURATION implicitly + 1 */,
    TIMEOUT = 5u /* ::android::hardware::wifi::supplicant::V1_2::DppFailureCode.BUSY implicitly + 1 */,
    FAILURE = 6u /* ::android::hardware::wifi::supplicant::V1_2::DppFailureCode.TIMEOUT implicitly + 1 */,
    NOT_SUPPORTED = 7u /* ::android::hardware::wifi::supplicant::V1_2::DppFailureCode.FAILURE implicitly + 1 */,
    CONFIGURATION_REJECTED = 8u /* ::android::hardware::wifi::supplicant::V1_2::DppFailureCode.NOT_SUPPORTED implicitly + 1 */,
    CANNOT_FIND_NETWORK = 9u /* ::android::hardware::wifi::supplicant::V1_3::DppFailureCode.CONFIGURATION_REJECTED implicitly + 1 */,
    ENROLLEE_AUTHENTICATION = 10u /* ::android::hardware::wifi::supplicant::V1_3::DppFailureCode.CANNOT_FIND_NETWORK implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::OcspType o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::OcspType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::OcspType lhs, const ::android::hardware::wifi::supplicant::V1_3::OcspType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::OcspType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::OcspType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::OcspType lhs, const ::android::hardware::wifi::supplicant::V1_3::OcspType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::OcspType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::OcspType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::OcspType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::OcspType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::WifiTechnology o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::WifiTechnology o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::WifiTechnology lhs, const ::android::hardware::wifi::supplicant::V1_3::WifiTechnology rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::WifiTechnology rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::WifiTechnology lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::WifiTechnology lhs, const ::android::hardware::wifi::supplicant::V1_3::WifiTechnology rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::WifiTechnology rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::WifiTechnology lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::WifiTechnology e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::WifiTechnology e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& o);
static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& lhs, const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& lhs, const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask lhs, const ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask lhs, const ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::DppProgressCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::DppProgressCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::DppProgressCode lhs, const ::android::hardware::wifi::supplicant::V1_3::DppProgressCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::DppProgressCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::DppProgressCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::DppProgressCode lhs, const ::android::hardware::wifi::supplicant::V1_3::DppProgressCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::DppProgressCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::DppProgressCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::DppProgressCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::DppProgressCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::DppSuccessCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::DppSuccessCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode lhs, const ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode lhs, const ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::DppFailureCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::DppFailureCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::DppFailureCode lhs, const ::android::hardware::wifi::supplicant::V1_3::DppFailureCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::DppFailureCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::DppFailureCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::DppFailureCode lhs, const ::android::hardware::wifi::supplicant::V1_3::DppFailureCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::DppFailureCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::DppFailureCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::DppFailureCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::DppFailureCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_3::OcspType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::OcspType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_3::OcspType::NONE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::OcspType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::OcspType::NONE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUEST_CERT_STATUS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::OcspType::REQUEST_CERT_STATUS)) {
        os += (first ? "" : " | ");
        os += "REQUEST_CERT_STATUS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUEST_CERT_STATUS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUIRE_CERT_STATUS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::OcspType::REQUIRE_CERT_STATUS)) {
        os += (first ? "" : " | ");
        os += "REQUIRE_CERT_STATUS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUIRE_CERT_STATUS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUIRE_ALL_CERTS_STATUS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::OcspType::REQUIRE_ALL_CERTS_STATUS)) {
        os += (first ? "" : " | ");
        os += "REQUIRE_ALL_CERTS_STATUS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUIRE_ALL_CERTS_STATUS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::OcspType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_3::OcspType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUEST_CERT_STATUS) {
        return "REQUEST_CERT_STATUS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUIRE_CERT_STATUS) {
        return "REQUIRE_CERT_STATUS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUIRE_ALL_CERTS_STATUS) {
        return "REQUIRE_ALL_CERTS_STATUS";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::OcspType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_3::WifiTechnology>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::WifiTechnology> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::WifiTechnology::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::UNKNOWN;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::LEGACY) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::WifiTechnology::LEGACY)) {
        os += (first ? "" : " | ");
        os += "LEGACY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::LEGACY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::HT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::WifiTechnology::HT)) {
        os += (first ? "" : " | ");
        os += "HT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::HT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::VHT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::WifiTechnology::VHT)) {
        os += (first ? "" : " | ");
        os += "VHT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::VHT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::HE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::WifiTechnology::HE)) {
        os += (first ? "" : " | ");
        os += "HE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::HE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::WifiTechnology o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::LEGACY) {
        return "LEGACY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::HT) {
        return "HT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::VHT) {
        return "VHT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::HE) {
        return "HE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::WifiTechnology o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".technology = ";
    os += ::android::hardware::wifi::supplicant::V1_3::toString(o.technology);
    os += ", .channelBandwidth = ";
    os += ::android::hardware::wifi::V1_0::toString(o.channelBandwidth);
    os += ", .maxNumberTxSpatialStreams = ";
    os += ::android::hardware::toString(o.maxNumberTxSpatialStreams);
    os += ", .maxNumberRxSpatialStreams = ";
    os += ::android::hardware::toString(o.maxNumberRxSpatialStreams);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& lhs, const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& rhs) {
    if (lhs.technology != rhs.technology) {
        return false;
    }
    if (lhs.channelBandwidth != rhs.channelBandwidth) {
        return false;
    }
    if (lhs.maxNumberTxSpatialStreams != rhs.maxNumberTxSpatialStreams) {
        return false;
    }
    if (lhs.maxNumberRxSpatialStreams != rhs.maxNumberRxSpatialStreams) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& lhs, const ::android::hardware::wifi::supplicant::V1_3::ConnectionCapabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask::MBO) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask::MBO)) {
        os += (first ? "" : " | ");
        os += "MBO";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask::MBO;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask::OCE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask::OCE)) {
        os += (first ? "" : " | ");
        os += "OCE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask::OCE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask::MBO) {
        return "MBO";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask::OCE) {
        return "OCE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_3::DppProgressCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::DppProgressCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::AUTHENTICATION_SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppProgressCode::AUTHENTICATION_SUCCESS)) {
        os += (first ? "" : " | ");
        os += "AUTHENTICATION_SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::AUTHENTICATION_SUCCESS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::RESPONSE_PENDING) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppProgressCode::RESPONSE_PENDING)) {
        os += (first ? "" : " | ");
        os += "RESPONSE_PENDING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::RESPONSE_PENDING;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::CONFIGURATION_SENT_WAITING_RESPONSE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppProgressCode::CONFIGURATION_SENT_WAITING_RESPONSE)) {
        os += (first ? "" : " | ");
        os += "CONFIGURATION_SENT_WAITING_RESPONSE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::CONFIGURATION_SENT_WAITING_RESPONSE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::CONFIGURATION_ACCEPTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppProgressCode::CONFIGURATION_ACCEPTED)) {
        os += (first ? "" : " | ");
        os += "CONFIGURATION_ACCEPTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::CONFIGURATION_ACCEPTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::DppProgressCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::AUTHENTICATION_SUCCESS) {
        return "AUTHENTICATION_SUCCESS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::RESPONSE_PENDING) {
        return "RESPONSE_PENDING";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::CONFIGURATION_SENT_WAITING_RESPONSE) {
        return "CONFIGURATION_SENT_WAITING_RESPONSE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::CONFIGURATION_ACCEPTED) {
        return "CONFIGURATION_ACCEPTED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::DppProgressCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_3::DppSuccessCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::DppSuccessCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode::CONFIGURATION_SENT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppSuccessCode::CONFIGURATION_SENT)) {
        os += (first ? "" : " | ");
        os += "CONFIGURATION_SENT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode::CONFIGURATION_SENT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode::CONFIGURATION_APPLIED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppSuccessCode::CONFIGURATION_APPLIED)) {
        os += (first ? "" : " | ");
        os += "CONFIGURATION_APPLIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode::CONFIGURATION_APPLIED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::DppSuccessCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode::CONFIGURATION_SENT) {
        return "CONFIGURATION_SENT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode::CONFIGURATION_APPLIED) {
        return "CONFIGURATION_APPLIED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::DppSuccessCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_3::DppFailureCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::DppFailureCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::INVALID_URI) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::INVALID_URI)) {
        os += (first ? "" : " | ");
        os += "INVALID_URI";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::INVALID_URI;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::AUTHENTICATION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::AUTHENTICATION)) {
        os += (first ? "" : " | ");
        os += "AUTHENTICATION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::AUTHENTICATION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::NOT_COMPATIBLE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::NOT_COMPATIBLE)) {
        os += (first ? "" : " | ");
        os += "NOT_COMPATIBLE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::NOT_COMPATIBLE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CONFIGURATION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CONFIGURATION)) {
        os += (first ? "" : " | ");
        os += "CONFIGURATION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CONFIGURATION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::BUSY) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::BUSY)) {
        os += (first ? "" : " | ");
        os += "BUSY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::BUSY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::TIMEOUT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::FAILURE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::FAILURE)) {
        os += (first ? "" : " | ");
        os += "FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CONFIGURATION_REJECTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CONFIGURATION_REJECTED)) {
        os += (first ? "" : " | ");
        os += "CONFIGURATION_REJECTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CONFIGURATION_REJECTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CANNOT_FIND_NETWORK) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CANNOT_FIND_NETWORK)) {
        os += (first ? "" : " | ");
        os += "CANNOT_FIND_NETWORK";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CANNOT_FIND_NETWORK;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::ENROLLEE_AUTHENTICATION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::DppFailureCode::ENROLLEE_AUTHENTICATION)) {
        os += (first ? "" : " | ");
        os += "ENROLLEE_AUTHENTICATION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::ENROLLEE_AUTHENTICATION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::DppFailureCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::INVALID_URI) {
        return "INVALID_URI";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::AUTHENTICATION) {
        return "AUTHENTICATION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::NOT_COMPATIBLE) {
        return "NOT_COMPATIBLE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CONFIGURATION) {
        return "CONFIGURATION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::BUSY) {
        return "BUSY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::TIMEOUT) {
        return "TIMEOUT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::FAILURE) {
        return "FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CONFIGURATION_REJECTED) {
        return "CONFIGURATION_REJECTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CANNOT_FIND_NETWORK) {
        return "CANNOT_FIND_NETWORK";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::ENROLLEE_AUTHENTICATION) {
        return "ENROLLEE_AUTHENTICATION";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::DppFailureCode o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_3
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_3::OcspType, 4> hidl_enum_values<::android::hardware::wifi::supplicant::V1_3::OcspType> = {
    ::android::hardware::wifi::supplicant::V1_3::OcspType::NONE,
    ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUEST_CERT_STATUS,
    ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUIRE_CERT_STATUS,
    ::android::hardware::wifi::supplicant::V1_3::OcspType::REQUIRE_ALL_CERTS_STATUS,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_3::WifiTechnology, 5> hidl_enum_values<::android::hardware::wifi::supplicant::V1_3::WifiTechnology> = {
    ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::UNKNOWN,
    ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::LEGACY,
    ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::HT,
    ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::VHT,
    ::android::hardware::wifi::supplicant::V1_3::WifiTechnology::HE,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask, 2> hidl_enum_values<::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask> = {
    ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask::MBO,
    ::android::hardware::wifi::supplicant::V1_3::WpaDriverCapabilitiesMask::OCE,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_3::DppProgressCode, 4> hidl_enum_values<::android::hardware::wifi::supplicant::V1_3::DppProgressCode> = {
    ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::AUTHENTICATION_SUCCESS,
    ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::RESPONSE_PENDING,
    ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::CONFIGURATION_SENT_WAITING_RESPONSE,
    ::android::hardware::wifi::supplicant::V1_3::DppProgressCode::CONFIGURATION_ACCEPTED,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_3::DppSuccessCode, 2> hidl_enum_values<::android::hardware::wifi::supplicant::V1_3::DppSuccessCode> = {
    ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode::CONFIGURATION_SENT,
    ::android::hardware::wifi::supplicant::V1_3::DppSuccessCode::CONFIGURATION_APPLIED,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_3::DppFailureCode, 11> hidl_enum_values<::android::hardware::wifi::supplicant::V1_3::DppFailureCode> = {
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::INVALID_URI,
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::AUTHENTICATION,
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::NOT_COMPATIBLE,
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CONFIGURATION,
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::BUSY,
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::TIMEOUT,
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::FAILURE,
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CONFIGURATION_REJECTED,
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::CANNOT_FIND_NETWORK,
    ::android::hardware::wifi::supplicant::V1_3::DppFailureCode::ENROLLEE_AUTHENTICATION,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_TYPES_H
