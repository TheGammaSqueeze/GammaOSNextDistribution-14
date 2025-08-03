#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace frameworks {
namespace sensorservice {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Result : int32_t;

enum class Result : int32_t {
    OK = 0,
    NOT_EXIST = 1 /* ::android::frameworks::sensorservice::V1_0::Result.OK implicitly + 1 */,
    NO_MEMORY = 2 /* ::android::frameworks::sensorservice::V1_0::Result.NOT_EXIST implicitly + 1 */,
    NO_INIT = 3 /* ::android::frameworks::sensorservice::V1_0::Result.NO_MEMORY implicitly + 1 */,
    PERMISSION_DENIED = 4 /* ::android::frameworks::sensorservice::V1_0::Result.NO_INIT implicitly + 1 */,
    BAD_VALUE = 5 /* ::android::frameworks::sensorservice::V1_0::Result.PERMISSION_DENIED implicitly + 1 */,
    INVALID_OPERATION = 6 /* ::android::frameworks::sensorservice::V1_0::Result.BAD_VALUE implicitly + 1 */,
    UNKNOWN_ERROR = 7 /* ::android::frameworks::sensorservice::V1_0::Result.INVALID_OPERATION implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::frameworks::sensorservice::V1_0::Result o);
static inline void PrintTo(::android::frameworks::sensorservice::V1_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::frameworks::sensorservice::V1_0::Result lhs, const ::android::frameworks::sensorservice::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::frameworks::sensorservice::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::frameworks::sensorservice::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::frameworks::sensorservice::V1_0::Result lhs, const ::android::frameworks::sensorservice::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::frameworks::sensorservice::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::frameworks::sensorservice::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::frameworks::sensorservice::V1_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::frameworks::sensorservice::V1_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::frameworks::sensorservice::V1_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::sensorservice::V1_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::sensorservice::V1_0::Result::OK) == static_cast<int32_t>(::android::frameworks::sensorservice::V1_0::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::frameworks::sensorservice::V1_0::Result::OK;
    }
    if ((o & ::android::frameworks::sensorservice::V1_0::Result::NOT_EXIST) == static_cast<int32_t>(::android::frameworks::sensorservice::V1_0::Result::NOT_EXIST)) {
        os += (first ? "" : " | ");
        os += "NOT_EXIST";
        first = false;
        flipped |= ::android::frameworks::sensorservice::V1_0::Result::NOT_EXIST;
    }
    if ((o & ::android::frameworks::sensorservice::V1_0::Result::NO_MEMORY) == static_cast<int32_t>(::android::frameworks::sensorservice::V1_0::Result::NO_MEMORY)) {
        os += (first ? "" : " | ");
        os += "NO_MEMORY";
        first = false;
        flipped |= ::android::frameworks::sensorservice::V1_0::Result::NO_MEMORY;
    }
    if ((o & ::android::frameworks::sensorservice::V1_0::Result::NO_INIT) == static_cast<int32_t>(::android::frameworks::sensorservice::V1_0::Result::NO_INIT)) {
        os += (first ? "" : " | ");
        os += "NO_INIT";
        first = false;
        flipped |= ::android::frameworks::sensorservice::V1_0::Result::NO_INIT;
    }
    if ((o & ::android::frameworks::sensorservice::V1_0::Result::PERMISSION_DENIED) == static_cast<int32_t>(::android::frameworks::sensorservice::V1_0::Result::PERMISSION_DENIED)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_DENIED";
        first = false;
        flipped |= ::android::frameworks::sensorservice::V1_0::Result::PERMISSION_DENIED;
    }
    if ((o & ::android::frameworks::sensorservice::V1_0::Result::BAD_VALUE) == static_cast<int32_t>(::android::frameworks::sensorservice::V1_0::Result::BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "BAD_VALUE";
        first = false;
        flipped |= ::android::frameworks::sensorservice::V1_0::Result::BAD_VALUE;
    }
    if ((o & ::android::frameworks::sensorservice::V1_0::Result::INVALID_OPERATION) == static_cast<int32_t>(::android::frameworks::sensorservice::V1_0::Result::INVALID_OPERATION)) {
        os += (first ? "" : " | ");
        os += "INVALID_OPERATION";
        first = false;
        flipped |= ::android::frameworks::sensorservice::V1_0::Result::INVALID_OPERATION;
    }
    if ((o & ::android::frameworks::sensorservice::V1_0::Result::UNKNOWN_ERROR) == static_cast<int32_t>(::android::frameworks::sensorservice::V1_0::Result::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::frameworks::sensorservice::V1_0::Result::UNKNOWN_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::sensorservice::V1_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::sensorservice::V1_0::Result::OK) {
        return "OK";
    }
    if (o == ::android::frameworks::sensorservice::V1_0::Result::NOT_EXIST) {
        return "NOT_EXIST";
    }
    if (o == ::android::frameworks::sensorservice::V1_0::Result::NO_MEMORY) {
        return "NO_MEMORY";
    }
    if (o == ::android::frameworks::sensorservice::V1_0::Result::NO_INIT) {
        return "NO_INIT";
    }
    if (o == ::android::frameworks::sensorservice::V1_0::Result::PERMISSION_DENIED) {
        return "PERMISSION_DENIED";
    }
    if (o == ::android::frameworks::sensorservice::V1_0::Result::BAD_VALUE) {
        return "BAD_VALUE";
    }
    if (o == ::android::frameworks::sensorservice::V1_0::Result::INVALID_OPERATION) {
        return "INVALID_OPERATION";
    }
    if (o == ::android::frameworks::sensorservice::V1_0::Result::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::sensorservice::V1_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace sensorservice
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
template<> inline constexpr std::array<::android::frameworks::sensorservice::V1_0::Result, 8> hidl_enum_values<::android::frameworks::sensorservice::V1_0::Result> = {
    ::android::frameworks::sensorservice::V1_0::Result::OK,
    ::android::frameworks::sensorservice::V1_0::Result::NOT_EXIST,
    ::android::frameworks::sensorservice::V1_0::Result::NO_MEMORY,
    ::android::frameworks::sensorservice::V1_0::Result::NO_INIT,
    ::android::frameworks::sensorservice::V1_0::Result::PERMISSION_DENIED,
    ::android::frameworks::sensorservice::V1_0::Result::BAD_VALUE,
    ::android::frameworks::sensorservice::V1_0::Result::INVALID_OPERATION,
    ::android::frameworks::sensorservice::V1_0::Result::UNKNOWN_ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_TYPES_H
