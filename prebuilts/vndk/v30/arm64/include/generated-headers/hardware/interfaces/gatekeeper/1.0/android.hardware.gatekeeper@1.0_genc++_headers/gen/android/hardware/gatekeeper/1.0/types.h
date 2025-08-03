#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gatekeeper {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class GatekeeperStatusCode : int32_t;
struct GatekeeperResponse;

/**
 * Gatekeeper response codes; success >= 0; error < 0
 */
enum class GatekeeperStatusCode : int32_t {
    STATUS_REENROLL = 1,
    STATUS_OK = 0,
    ERROR_GENERAL_FAILURE = -1 /* -1 */,
    ERROR_RETRY_TIMEOUT = -2 /* -2 */,
    ERROR_NOT_IMPLEMENTED = -3 /* -3 */,
};

/**
 * Gatekeeper response to any/all requests has this structure as mandatory part
 */
struct GatekeeperResponse final {
    /**
     * request completion status
     */
    ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode code __attribute__ ((aligned(4)));
    /**
     * retry timeout in ms, if code == ERROR_RETRY_TIMEOUT
     * otherwise unused (0)
     */
    uint32_t timeout __attribute__ ((aligned(4)));
    /**
     * optional crypto blob. Opaque to Android system.
     */
    ::android::hardware::hidl_vec<uint8_t> data __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::gatekeeper::V1_0::GatekeeperResponse, code) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::gatekeeper::V1_0::GatekeeperResponse, timeout) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::gatekeeper::V1_0::GatekeeperResponse, data) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::gatekeeper::V1_0::GatekeeperResponse) == 24, "wrong size");
static_assert(__alignof(::android::hardware::gatekeeper::V1_0::GatekeeperResponse) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode o);
static inline void PrintTo(::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode lhs, const ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode lhs, const ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& o);
static inline void PrintTo(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& lhs, const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& rhs);
static inline bool operator!=(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& lhs, const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::STATUS_REENROLL) == static_cast<int32_t>(::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::STATUS_REENROLL)) {
        os += (first ? "" : " | ");
        os += "STATUS_REENROLL";
        first = false;
        flipped |= ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::STATUS_REENROLL;
    }
    if ((o & ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::STATUS_OK) == static_cast<int32_t>(::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::STATUS_OK)) {
        os += (first ? "" : " | ");
        os += "STATUS_OK";
        first = false;
        flipped |= ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::STATUS_OK;
    }
    if ((o & ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_GENERAL_FAILURE) == static_cast<int32_t>(::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_GENERAL_FAILURE)) {
        os += (first ? "" : " | ");
        os += "ERROR_GENERAL_FAILURE";
        first = false;
        flipped |= ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_GENERAL_FAILURE;
    }
    if ((o & ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_RETRY_TIMEOUT) == static_cast<int32_t>(::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_RETRY_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "ERROR_RETRY_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_RETRY_TIMEOUT;
    }
    if ((o & ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED) == static_cast<int32_t>(::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED)) {
        os += (first ? "" : " | ");
        os += "ERROR_NOT_IMPLEMENTED";
        first = false;
        flipped |= ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::STATUS_REENROLL) {
        return "STATUS_REENROLL";
    }
    if (o == ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::STATUS_OK) {
        return "STATUS_OK";
    }
    if (o == ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_GENERAL_FAILURE) {
        return "ERROR_GENERAL_FAILURE";
    }
    if (o == ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_RETRY_TIMEOUT) {
        return "ERROR_RETRY_TIMEOUT";
    }
    if (o == ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED) {
        return "ERROR_NOT_IMPLEMENTED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".code = ";
    os += ::android::hardware::gatekeeper::V1_0::toString(o.code);
    os += ", .timeout = ";
    os += ::android::hardware::toString(o.timeout);
    os += ", .data = ";
    os += ::android::hardware::toString(o.data);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& lhs, const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& rhs) {
    if (lhs.code != rhs.code) {
        return false;
    }
    if (lhs.timeout != rhs.timeout) {
        return false;
    }
    if (lhs.data != rhs.data) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& lhs, const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace gatekeeper
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
template<> inline constexpr std::array<::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode, 5> hidl_enum_values<::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode> = {
    ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::STATUS_REENROLL,
    ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::STATUS_OK,
    ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_GENERAL_FAILURE,
    ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_RETRY_TIMEOUT,
    ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_TYPES_H
