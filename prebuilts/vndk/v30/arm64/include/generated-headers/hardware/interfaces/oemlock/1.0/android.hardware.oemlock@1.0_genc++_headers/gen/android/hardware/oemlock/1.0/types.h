#ifndef HIDL_GENERATED_ANDROID_HARDWARE_OEMLOCK_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_OEMLOCK_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace oemlock {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class OemLockStatus : uint32_t;
enum class OemLockSecureStatus : uint32_t;

enum class OemLockStatus : uint32_t {
    /**
     * The operation completed successfully.
     */
    OK = 0u,
    /**
     * The operation encountered a problem.
     */
    FAILED = 1u /* ::android::hardware::oemlock::V1_0::OemLockStatus.OK implicitly + 1 */,
};

enum class OemLockSecureStatus : uint32_t {
    /**
     * The operation completed successfully.
     */
    OK = 0u,
    /**
     * The operation encountered a problem.
     */
    FAILED = 1u /* ::android::hardware::oemlock::V1_0::OemLockStatus.OK implicitly + 1 */,
    /**
     * An invalid signature was provided so the operation was not performed.
     */
    INVALID_SIGNATURE = 2u /* ::android::hardware::oemlock::V1_0::OemLockStatus.FAILED implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::oemlock::V1_0::OemLockStatus o);
static inline void PrintTo(::android::hardware::oemlock::V1_0::OemLockStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::oemlock::V1_0::OemLockStatus lhs, const ::android::hardware::oemlock::V1_0::OemLockStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::oemlock::V1_0::OemLockStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::oemlock::V1_0::OemLockStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::oemlock::V1_0::OemLockStatus lhs, const ::android::hardware::oemlock::V1_0::OemLockStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::oemlock::V1_0::OemLockStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::oemlock::V1_0::OemLockStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::oemlock::V1_0::OemLockStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::oemlock::V1_0::OemLockStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::oemlock::V1_0::OemLockSecureStatus o);
static inline void PrintTo(::android::hardware::oemlock::V1_0::OemLockSecureStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::oemlock::V1_0::OemLockSecureStatus lhs, const ::android::hardware::oemlock::V1_0::OemLockSecureStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::oemlock::V1_0::OemLockSecureStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::oemlock::V1_0::OemLockSecureStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::oemlock::V1_0::OemLockSecureStatus lhs, const ::android::hardware::oemlock::V1_0::OemLockSecureStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::oemlock::V1_0::OemLockSecureStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::oemlock::V1_0::OemLockSecureStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::oemlock::V1_0::OemLockSecureStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::oemlock::V1_0::OemLockSecureStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::oemlock::V1_0::OemLockStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::oemlock::V1_0::OemLockStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::oemlock::V1_0::OemLockStatus::OK) == static_cast<uint32_t>(::android::hardware::oemlock::V1_0::OemLockStatus::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::oemlock::V1_0::OemLockStatus::OK;
    }
    if ((o & ::android::hardware::oemlock::V1_0::OemLockStatus::FAILED) == static_cast<uint32_t>(::android::hardware::oemlock::V1_0::OemLockStatus::FAILED)) {
        os += (first ? "" : " | ");
        os += "FAILED";
        first = false;
        flipped |= ::android::hardware::oemlock::V1_0::OemLockStatus::FAILED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::oemlock::V1_0::OemLockStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::oemlock::V1_0::OemLockStatus::OK) {
        return "OK";
    }
    if (o == ::android::hardware::oemlock::V1_0::OemLockStatus::FAILED) {
        return "FAILED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::oemlock::V1_0::OemLockStatus o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::oemlock::V1_0::OemLockSecureStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::oemlock::V1_0::OemLockSecureStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::oemlock::V1_0::OemLockSecureStatus::OK) == static_cast<uint32_t>(::android::hardware::oemlock::V1_0::OemLockSecureStatus::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::oemlock::V1_0::OemLockSecureStatus::OK;
    }
    if ((o & ::android::hardware::oemlock::V1_0::OemLockSecureStatus::FAILED) == static_cast<uint32_t>(::android::hardware::oemlock::V1_0::OemLockSecureStatus::FAILED)) {
        os += (first ? "" : " | ");
        os += "FAILED";
        first = false;
        flipped |= ::android::hardware::oemlock::V1_0::OemLockSecureStatus::FAILED;
    }
    if ((o & ::android::hardware::oemlock::V1_0::OemLockSecureStatus::INVALID_SIGNATURE) == static_cast<uint32_t>(::android::hardware::oemlock::V1_0::OemLockSecureStatus::INVALID_SIGNATURE)) {
        os += (first ? "" : " | ");
        os += "INVALID_SIGNATURE";
        first = false;
        flipped |= ::android::hardware::oemlock::V1_0::OemLockSecureStatus::INVALID_SIGNATURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::oemlock::V1_0::OemLockSecureStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::oemlock::V1_0::OemLockSecureStatus::OK) {
        return "OK";
    }
    if (o == ::android::hardware::oemlock::V1_0::OemLockSecureStatus::FAILED) {
        return "FAILED";
    }
    if (o == ::android::hardware::oemlock::V1_0::OemLockSecureStatus::INVALID_SIGNATURE) {
        return "INVALID_SIGNATURE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::oemlock::V1_0::OemLockSecureStatus o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace oemlock
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
template<> inline constexpr std::array<::android::hardware::oemlock::V1_0::OemLockStatus, 2> hidl_enum_values<::android::hardware::oemlock::V1_0::OemLockStatus> = {
    ::android::hardware::oemlock::V1_0::OemLockStatus::OK,
    ::android::hardware::oemlock::V1_0::OemLockStatus::FAILED,
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
template<> inline constexpr std::array<::android::hardware::oemlock::V1_0::OemLockSecureStatus, 3> hidl_enum_values<::android::hardware::oemlock::V1_0::OemLockSecureStatus> = {
    ::android::hardware::oemlock::V1_0::OemLockSecureStatus::OK,
    ::android::hardware::oemlock::V1_0::OemLockSecureStatus::FAILED,
    ::android::hardware::oemlock::V1_0::OemLockSecureStatus::INVALID_SIGNATURE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_OEMLOCK_V1_0_TYPES_H
