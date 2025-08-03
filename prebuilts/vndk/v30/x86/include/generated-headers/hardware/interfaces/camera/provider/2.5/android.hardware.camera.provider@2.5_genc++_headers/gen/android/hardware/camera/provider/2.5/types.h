#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_5_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_5_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace V2_5 {

// Forward declaration for forward reference support:
enum class DeviceState : uint64_t;

/**
 * DeviceState:
 *
 * Possible physical states of the overall device, for use with
 * ICameraProvider::notifyDeviceStateChange.
 *
 */
enum class DeviceState : uint64_t {
    /**
     * The device is in its normal physical configuration. This is the default if the
     * device does not support multiple different states.
     */
    NORMAL = 0ull,
    /**
     * Camera device(s) facing backward are covered.
     */
    BACK_COVERED = 1ull /* 1 << 0 */,
    /**
     * Camera device(s) facing foward are covered.
     */
    FRONT_COVERED = 2ull /* 1 << 1 */,
    /**
     * The device is folded.  If not set, the device is unfolded or does not
     * support folding.
     *
     * The exact point when this status change happens during the folding
     * operation is device-specific.
     */
    FOLDED = 4ull /* 1 << 2 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint64_t o);
static inline std::string toString(::android::hardware::camera::provider::V2_5::DeviceState o);
static inline void PrintTo(::android::hardware::camera::provider::V2_5::DeviceState o, ::std::ostream* os);
constexpr uint64_t operator|(const ::android::hardware::camera::provider::V2_5::DeviceState lhs, const ::android::hardware::camera::provider::V2_5::DeviceState rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const uint64_t lhs, const ::android::hardware::camera::provider::V2_5::DeviceState rhs) {
    return static_cast<uint64_t>(lhs | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const ::android::hardware::camera::provider::V2_5::DeviceState lhs, const uint64_t rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | rhs);
}
constexpr uint64_t operator&(const ::android::hardware::camera::provider::V2_5::DeviceState lhs, const ::android::hardware::camera::provider::V2_5::DeviceState rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const uint64_t lhs, const ::android::hardware::camera::provider::V2_5::DeviceState rhs) {
    return static_cast<uint64_t>(lhs & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const ::android::hardware::camera::provider::V2_5::DeviceState lhs, const uint64_t rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & rhs);
}
constexpr uint64_t &operator|=(uint64_t& v, const ::android::hardware::camera::provider::V2_5::DeviceState e) {
    v |= static_cast<uint64_t>(e);
    return v;
}
constexpr uint64_t &operator&=(uint64_t& v, const ::android::hardware::camera::provider::V2_5::DeviceState e) {
    v &= static_cast<uint64_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::camera::provider::V2_5::DeviceState>(uint64_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::provider::V2_5::DeviceState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::provider::V2_5::DeviceState::NORMAL) == static_cast<uint64_t>(::android::hardware::camera::provider::V2_5::DeviceState::NORMAL)) {
        os += (first ? "" : " | ");
        os += "NORMAL";
        first = false;
        flipped |= ::android::hardware::camera::provider::V2_5::DeviceState::NORMAL;
    }
    if ((o & ::android::hardware::camera::provider::V2_5::DeviceState::BACK_COVERED) == static_cast<uint64_t>(::android::hardware::camera::provider::V2_5::DeviceState::BACK_COVERED)) {
        os += (first ? "" : " | ");
        os += "BACK_COVERED";
        first = false;
        flipped |= ::android::hardware::camera::provider::V2_5::DeviceState::BACK_COVERED;
    }
    if ((o & ::android::hardware::camera::provider::V2_5::DeviceState::FRONT_COVERED) == static_cast<uint64_t>(::android::hardware::camera::provider::V2_5::DeviceState::FRONT_COVERED)) {
        os += (first ? "" : " | ");
        os += "FRONT_COVERED";
        first = false;
        flipped |= ::android::hardware::camera::provider::V2_5::DeviceState::FRONT_COVERED;
    }
    if ((o & ::android::hardware::camera::provider::V2_5::DeviceState::FOLDED) == static_cast<uint64_t>(::android::hardware::camera::provider::V2_5::DeviceState::FOLDED)) {
        os += (first ? "" : " | ");
        os += "FOLDED";
        first = false;
        flipped |= ::android::hardware::camera::provider::V2_5::DeviceState::FOLDED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::provider::V2_5::DeviceState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::provider::V2_5::DeviceState::NORMAL) {
        return "NORMAL";
    }
    if (o == ::android::hardware::camera::provider::V2_5::DeviceState::BACK_COVERED) {
        return "BACK_COVERED";
    }
    if (o == ::android::hardware::camera::provider::V2_5::DeviceState::FRONT_COVERED) {
        return "FRONT_COVERED";
    }
    if (o == ::android::hardware::camera::provider::V2_5::DeviceState::FOLDED) {
        return "FOLDED";
    }
    std::string os;
    os += toHexString(static_cast<uint64_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::provider::V2_5::DeviceState o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V2_5
}  // namespace provider
}  // namespace camera
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
template<> inline constexpr std::array<::android::hardware::camera::provider::V2_5::DeviceState, 4> hidl_enum_values<::android::hardware::camera::provider::V2_5::DeviceState> = {
    ::android::hardware::camera::provider::V2_5::DeviceState::NORMAL,
    ::android::hardware::camera::provider::V2_5::DeviceState::BACK_COVERED,
    ::android::hardware::camera::provider::V2_5::DeviceState::FRONT_COVERED,
    ::android::hardware::camera::provider::V2_5::DeviceState::FOLDED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_5_TYPES_H
