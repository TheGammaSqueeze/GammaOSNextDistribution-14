#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_DISPLAYSERVICE_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_DISPLAYSERVICE_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace frameworks {
namespace displayservice {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;

enum class Status : uint32_t {
    SUCCESS = 0u,
    BAD_VALUE = 1u /* ::android::frameworks::displayservice::V1_0::Status.SUCCESS implicitly + 1 */,
    UNKNOWN = 2u /* ::android::frameworks::displayservice::V1_0::Status.BAD_VALUE implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::frameworks::displayservice::V1_0::Status o);
static inline void PrintTo(::android::frameworks::displayservice::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::frameworks::displayservice::V1_0::Status lhs, const ::android::frameworks::displayservice::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::frameworks::displayservice::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::frameworks::displayservice::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::frameworks::displayservice::V1_0::Status lhs, const ::android::frameworks::displayservice::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::frameworks::displayservice::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::frameworks::displayservice::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::frameworks::displayservice::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::frameworks::displayservice::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::frameworks::displayservice::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::displayservice::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::displayservice::V1_0::Status::SUCCESS) == static_cast<uint32_t>(::android::frameworks::displayservice::V1_0::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::frameworks::displayservice::V1_0::Status::SUCCESS;
    }
    if ((o & ::android::frameworks::displayservice::V1_0::Status::BAD_VALUE) == static_cast<uint32_t>(::android::frameworks::displayservice::V1_0::Status::BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "BAD_VALUE";
        first = false;
        flipped |= ::android::frameworks::displayservice::V1_0::Status::BAD_VALUE;
    }
    if ((o & ::android::frameworks::displayservice::V1_0::Status::UNKNOWN) == static_cast<uint32_t>(::android::frameworks::displayservice::V1_0::Status::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::frameworks::displayservice::V1_0::Status::UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::displayservice::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::displayservice::V1_0::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::frameworks::displayservice::V1_0::Status::BAD_VALUE) {
        return "BAD_VALUE";
    }
    if (o == ::android::frameworks::displayservice::V1_0::Status::UNKNOWN) {
        return "UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::displayservice::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace displayservice
}  // namespace frameworks
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::frameworks::displayservice::V1_0::Status, 3> hidl_enum_values<::android::frameworks::displayservice::V1_0::Status> = {
    ::android::frameworks::displayservice::V1_0::Status::SUCCESS,
    ::android::frameworks::displayservice::V1_0::Status::BAD_VALUE,
    ::android::frameworks::displayservice::V1_0::Status::UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_DISPLAYSERVICE_V1_0_TYPES_H
