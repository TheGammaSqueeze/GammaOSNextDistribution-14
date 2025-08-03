#ifndef HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class WakeLockType : uint32_t;

/**
 * Blocking CPU suspend is the only constraint that must be respected by all
 * wake lock types. E.g. a request for a full wake lock must block CPU suspend,
 * but not necessarily keep the screen alive.
 */
enum class WakeLockType : uint32_t {
    /*
     * CPU stays on.
     */
    PARTIAL = 0u,
    /*
     * CPU and the screen stay on.
     */
    FULL = 1u /* ::android::system::suspend::V1_0::WakeLockType.PARTIAL implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::system::suspend::V1_0::WakeLockType o);
static inline void PrintTo(::android::system::suspend::V1_0::WakeLockType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::system::suspend::V1_0::WakeLockType lhs, const ::android::system::suspend::V1_0::WakeLockType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::system::suspend::V1_0::WakeLockType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::system::suspend::V1_0::WakeLockType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::system::suspend::V1_0::WakeLockType lhs, const ::android::system::suspend::V1_0::WakeLockType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::system::suspend::V1_0::WakeLockType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::system::suspend::V1_0::WakeLockType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::system::suspend::V1_0::WakeLockType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::system::suspend::V1_0::WakeLockType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::system::suspend::V1_0::WakeLockType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::system::suspend::V1_0::WakeLockType> flipped = 0;
    bool first = true;
    if ((o & ::android::system::suspend::V1_0::WakeLockType::PARTIAL) == static_cast<uint32_t>(::android::system::suspend::V1_0::WakeLockType::PARTIAL)) {
        os += (first ? "" : " | ");
        os += "PARTIAL";
        first = false;
        flipped |= ::android::system::suspend::V1_0::WakeLockType::PARTIAL;
    }
    if ((o & ::android::system::suspend::V1_0::WakeLockType::FULL) == static_cast<uint32_t>(::android::system::suspend::V1_0::WakeLockType::FULL)) {
        os += (first ? "" : " | ");
        os += "FULL";
        first = false;
        flipped |= ::android::system::suspend::V1_0::WakeLockType::FULL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::system::suspend::V1_0::WakeLockType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::system::suspend::V1_0::WakeLockType::PARTIAL) {
        return "PARTIAL";
    }
    if (o == ::android::system::suspend::V1_0::WakeLockType::FULL) {
        return "FULL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::system::suspend::V1_0::WakeLockType o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace suspend
}  // namespace system
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::system::suspend::V1_0::WakeLockType, 2> hidl_enum_values<::android::system::suspend::V1_0::WakeLockType> = {
    ::android::system::suspend::V1_0::WakeLockType::PARTIAL,
    ::android::system::suspend::V1_0::WakeLockType::FULL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_TYPES_H
