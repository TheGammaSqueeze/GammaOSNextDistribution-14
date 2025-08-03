#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace bluetooth {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : int32_t;

enum class Status : int32_t {
    SUCCESS = 0,
    TRANSPORT_ERROR = 1 /* ::android::hardware::bluetooth::V1_0::Status.SUCCESS implicitly + 1 */,
    INITIALIZATION_ERROR = 2 /* ::android::hardware::bluetooth::V1_0::Status.TRANSPORT_ERROR implicitly + 1 */,
    UNKNOWN = 3 /* ::android::hardware::bluetooth::V1_0::Status.INITIALIZATION_ERROR implicitly + 1 */,
};

/**
 * HCI packets are transmitted as a vector of type uint8_t.
 */
typedef ::android::hardware::hidl_vec<uint8_t> HciPacket;

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::bluetooth::V1_0::Status o);
static inline void PrintTo(::android::hardware::bluetooth::V1_0::Status o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::bluetooth::V1_0::Status lhs, const ::android::hardware::bluetooth::V1_0::Status rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::bluetooth::V1_0::Status rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::bluetooth::V1_0::Status lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::bluetooth::V1_0::Status lhs, const ::android::hardware::bluetooth::V1_0::Status rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::bluetooth::V1_0::Status rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::bluetooth::V1_0::Status lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::bluetooth::V1_0::Status e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::bluetooth::V1_0::Status e) {
    v &= static_cast<int32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::bluetooth::V1_0::Status>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::bluetooth::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::bluetooth::V1_0::Status::SUCCESS) == static_cast<int32_t>(::android::hardware::bluetooth::V1_0::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::bluetooth::V1_0::Status::SUCCESS;
    }
    if ((o & ::android::hardware::bluetooth::V1_0::Status::TRANSPORT_ERROR) == static_cast<int32_t>(::android::hardware::bluetooth::V1_0::Status::TRANSPORT_ERROR)) {
        os += (first ? "" : " | ");
        os += "TRANSPORT_ERROR";
        first = false;
        flipped |= ::android::hardware::bluetooth::V1_0::Status::TRANSPORT_ERROR;
    }
    if ((o & ::android::hardware::bluetooth::V1_0::Status::INITIALIZATION_ERROR) == static_cast<int32_t>(::android::hardware::bluetooth::V1_0::Status::INITIALIZATION_ERROR)) {
        os += (first ? "" : " | ");
        os += "INITIALIZATION_ERROR";
        first = false;
        flipped |= ::android::hardware::bluetooth::V1_0::Status::INITIALIZATION_ERROR;
    }
    if ((o & ::android::hardware::bluetooth::V1_0::Status::UNKNOWN) == static_cast<int32_t>(::android::hardware::bluetooth::V1_0::Status::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::bluetooth::V1_0::Status::UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::bluetooth::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::bluetooth::V1_0::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::bluetooth::V1_0::Status::TRANSPORT_ERROR) {
        return "TRANSPORT_ERROR";
    }
    if (o == ::android::hardware::bluetooth::V1_0::Status::INITIALIZATION_ERROR) {
        return "INITIALIZATION_ERROR";
    }
    if (o == ::android::hardware::bluetooth::V1_0::Status::UNKNOWN) {
        return "UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::bluetooth::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace bluetooth
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
template<> inline constexpr std::array<::android::hardware::bluetooth::V1_0::Status, 4> hidl_enum_values<::android::hardware::bluetooth::V1_0::Status> = {
    ::android::hardware::bluetooth::V1_0::Status::SUCCESS,
    ::android::hardware::bluetooth::V1_0::Status::TRANSPORT_ERROR,
    ::android::hardware::bluetooth::V1_0::Status::INITIALIZATION_ERROR,
    ::android::hardware::bluetooth::V1_0::Status::UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_V1_0_TYPES_H
