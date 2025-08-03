#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_2 {

// Forward declaration for forward reference support:
enum class DppAkm : uint32_t;
enum class DppNetRole : uint32_t;
enum class DppProgressCode : uint32_t;
enum class DppFailureCode : uint32_t;

/**
 * DppAkm: The various AKMs that can be provisioned using DPP.
 */
enum class DppAkm : uint32_t {
    PSK = 0u,
    PSK_SAE = 1u /* ::android::hardware::wifi::supplicant::V1_2::DppAkm.PSK implicitly + 1 */,
    SAE = 2u /* ::android::hardware::wifi::supplicant::V1_2::DppAkm.PSK_SAE implicitly + 1 */,
    DPP = 3u /* ::android::hardware::wifi::supplicant::V1_2::DppAkm.SAE implicitly + 1 */,
};

/**
 * DppNetRole: The network role that the configurator offers the enrollee.
 */
enum class DppNetRole : uint32_t {
    STA = 0u,
    AP = 1u /* ::android::hardware::wifi::supplicant::V1_2::DppNetRole.STA implicitly + 1 */,
};

/**
 * DppProgressCode: Progress codes for DPP (Easy Connect)
 */
enum class DppProgressCode : uint32_t {
    AUTHENTICATION_SUCCESS = 0u,
    RESPONSE_PENDING = 1u /* ::android::hardware::wifi::supplicant::V1_2::DppProgressCode.AUTHENTICATION_SUCCESS implicitly + 1 */,
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
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_2::DppAkm o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_2::DppAkm o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_2::DppAkm lhs, const ::android::hardware::wifi::supplicant::V1_2::DppAkm rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_2::DppAkm rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_2::DppAkm lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_2::DppAkm lhs, const ::android::hardware::wifi::supplicant::V1_2::DppAkm rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_2::DppAkm rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_2::DppAkm lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_2::DppAkm e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_2::DppAkm e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_2::DppNetRole o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_2::DppNetRole o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_2::DppNetRole lhs, const ::android::hardware::wifi::supplicant::V1_2::DppNetRole rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_2::DppNetRole rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_2::DppNetRole lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_2::DppNetRole lhs, const ::android::hardware::wifi::supplicant::V1_2::DppNetRole rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_2::DppNetRole rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_2::DppNetRole lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_2::DppNetRole e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_2::DppNetRole e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_2::DppProgressCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_2::DppProgressCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_2::DppProgressCode lhs, const ::android::hardware::wifi::supplicant::V1_2::DppProgressCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_2::DppProgressCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_2::DppProgressCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_2::DppProgressCode lhs, const ::android::hardware::wifi::supplicant::V1_2::DppProgressCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_2::DppProgressCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_2::DppProgressCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_2::DppProgressCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_2::DppProgressCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_2::DppFailureCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_2::DppFailureCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_2::DppFailureCode lhs, const ::android::hardware::wifi::supplicant::V1_2::DppFailureCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_2::DppFailureCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_2::DppFailureCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_2::DppFailureCode lhs, const ::android::hardware::wifi::supplicant::V1_2::DppFailureCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_2::DppFailureCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_2::DppFailureCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_2::DppFailureCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_2::DppFailureCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_2::DppAkm>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_2::DppAkm> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppAkm::PSK) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppAkm::PSK)) {
        os += (first ? "" : " | ");
        os += "PSK";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppAkm::PSK;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppAkm::PSK_SAE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppAkm::PSK_SAE)) {
        os += (first ? "" : " | ");
        os += "PSK_SAE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppAkm::PSK_SAE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppAkm::SAE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppAkm::SAE)) {
        os += (first ? "" : " | ");
        os += "SAE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppAkm::SAE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppAkm::DPP) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppAkm::DPP)) {
        os += (first ? "" : " | ");
        os += "DPP";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppAkm::DPP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_2::DppAkm o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppAkm::PSK) {
        return "PSK";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppAkm::PSK_SAE) {
        return "PSK_SAE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppAkm::SAE) {
        return "SAE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppAkm::DPP) {
        return "DPP";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_2::DppAkm o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_2::DppNetRole>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_2::DppNetRole> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppNetRole::STA) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppNetRole::STA)) {
        os += (first ? "" : " | ");
        os += "STA";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppNetRole::STA;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppNetRole::AP) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppNetRole::AP)) {
        os += (first ? "" : " | ");
        os += "AP";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppNetRole::AP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_2::DppNetRole o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppNetRole::STA) {
        return "STA";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppNetRole::AP) {
        return "AP";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_2::DppNetRole o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_2::DppProgressCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_2::DppProgressCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppProgressCode::AUTHENTICATION_SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppProgressCode::AUTHENTICATION_SUCCESS)) {
        os += (first ? "" : " | ");
        os += "AUTHENTICATION_SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppProgressCode::AUTHENTICATION_SUCCESS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppProgressCode::RESPONSE_PENDING) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppProgressCode::RESPONSE_PENDING)) {
        os += (first ? "" : " | ");
        os += "RESPONSE_PENDING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppProgressCode::RESPONSE_PENDING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_2::DppProgressCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppProgressCode::AUTHENTICATION_SUCCESS) {
        return "AUTHENTICATION_SUCCESS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppProgressCode::RESPONSE_PENDING) {
        return "RESPONSE_PENDING";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_2::DppProgressCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_2::DppFailureCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_2::DppFailureCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::INVALID_URI) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppFailureCode::INVALID_URI)) {
        os += (first ? "" : " | ");
        os += "INVALID_URI";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::INVALID_URI;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::AUTHENTICATION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppFailureCode::AUTHENTICATION)) {
        os += (first ? "" : " | ");
        os += "AUTHENTICATION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::AUTHENTICATION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::NOT_COMPATIBLE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppFailureCode::NOT_COMPATIBLE)) {
        os += (first ? "" : " | ");
        os += "NOT_COMPATIBLE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::NOT_COMPATIBLE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::CONFIGURATION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppFailureCode::CONFIGURATION)) {
        os += (first ? "" : " | ");
        os += "CONFIGURATION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::CONFIGURATION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::BUSY) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppFailureCode::BUSY)) {
        os += (first ? "" : " | ");
        os += "BUSY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::BUSY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::TIMEOUT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppFailureCode::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::FAILURE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppFailureCode::FAILURE)) {
        os += (first ? "" : " | ");
        os += "FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_2::DppFailureCode::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::NOT_SUPPORTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_2::DppFailureCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::INVALID_URI) {
        return "INVALID_URI";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::AUTHENTICATION) {
        return "AUTHENTICATION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::NOT_COMPATIBLE) {
        return "NOT_COMPATIBLE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::CONFIGURATION) {
        return "CONFIGURATION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::BUSY) {
        return "BUSY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::TIMEOUT) {
        return "TIMEOUT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::FAILURE) {
        return "FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_2::DppFailureCode o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_2
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_2::DppAkm, 4> hidl_enum_values<::android::hardware::wifi::supplicant::V1_2::DppAkm> = {
    ::android::hardware::wifi::supplicant::V1_2::DppAkm::PSK,
    ::android::hardware::wifi::supplicant::V1_2::DppAkm::PSK_SAE,
    ::android::hardware::wifi::supplicant::V1_2::DppAkm::SAE,
    ::android::hardware::wifi::supplicant::V1_2::DppAkm::DPP,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_2::DppNetRole, 2> hidl_enum_values<::android::hardware::wifi::supplicant::V1_2::DppNetRole> = {
    ::android::hardware::wifi::supplicant::V1_2::DppNetRole::STA,
    ::android::hardware::wifi::supplicant::V1_2::DppNetRole::AP,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_2::DppProgressCode, 2> hidl_enum_values<::android::hardware::wifi::supplicant::V1_2::DppProgressCode> = {
    ::android::hardware::wifi::supplicant::V1_2::DppProgressCode::AUTHENTICATION_SUCCESS,
    ::android::hardware::wifi::supplicant::V1_2::DppProgressCode::RESPONSE_PENDING,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_2::DppFailureCode, 8> hidl_enum_values<::android::hardware::wifi::supplicant::V1_2::DppFailureCode> = {
    ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::INVALID_URI,
    ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::AUTHENTICATION,
    ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::NOT_COMPATIBLE,
    ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::CONFIGURATION,
    ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::BUSY,
    ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::TIMEOUT,
    ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::FAILURE,
    ::android::hardware::wifi::supplicant::V1_2::DppFailureCode::NOT_SUPPORTED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_TYPES_H
