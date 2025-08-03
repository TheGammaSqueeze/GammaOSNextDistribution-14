#ifndef HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_3_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_3_TYPES_H

#include <android/hardware/power/1.2/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace power {
namespace V1_3 {

// Forward declaration for forward reference support:
enum class PowerHint : uint32_t;

/**
 * Power hint identifiers passed to powerHintAsync_1_3()
 */
enum class PowerHint : uint32_t {
    /**
     * Foreground app has started or stopped requesting a VSYNC pulse
     * from SurfaceFlinger. If the app has started requesting VSYNC
     * then CPU and GPU load is expected soon, and it may be appropriate
     * to raise speeds of CPU, memory bus, etc. The data parameter is
     * non-zero to indicate VSYNC pulse is now requested, or zero for
     * VSYNC pulse no longer requested.
     */
    VSYNC = 1u /* 0x00000001 */,
    /**
     * User is interacting with the device, for example, touchscreen
     * events are incoming. CPU and GPU load may be expected soon,
     * and it may be appropriate to raise speeds of CPU, memory bus,
     * etc. The data parameter is the estimated length of the interaction
     * in milliseconds, or 0 if unknown.
     */
    INTERACTION = 2u /* 0x00000002 */,
    /**
     * DO NOT USE VIDEO_ENCODE/_DECODE!  They will be removed in
     * KLP.
     */
    VIDEO_ENCODE = 3u /* 0x00000003 */,
    VIDEO_DECODE = 4u /* 0x00000004 */,
    /**
     * Low power mode is activated or deactivated. Low power mode
     * is intended to save battery at the cost of performance. The data
     * parameter is non-zero when low power mode is activated, and zero
     * when deactivated.
     */
    LOW_POWER = 5u /* 0x00000005 */,
    /**
     * Sustained Performance mode is actived or deactivated. Sustained
     * performance mode is intended to provide a consistent level of
     * performance for a prolonged amount of time. The data parameter is
     * non-zero when sustained performance mode is activated, and zero
     * when deactivated.
     */
    SUSTAINED_PERFORMANCE = 6u /* 0x00000006 */,
    /**
     * VR Mode is activated or deactivated. VR mode is intended to
     * provide minimum guarantee for performance for the amount of time the
     * device can sustain it. The data parameter is non-zero when the mode
     * is activated and zero when deactivated.
     */
    VR_MODE = 7u /* 0x00000007 */,
    /**
     * This hint indicates that an application has been launched. Can be used
     * for device specific optimizations during application launch. The data
     * parameter is non-zero when the application starts to launch and zero when
     * it has been launched.
     */
    LAUNCH = 8u /* 0x00000008 */,
    /**
     * This hint indicates that audio stream is being started. Can be used
     * for device specific optimizations during starting audio stream. The
     * data parameter is non-zero when stream starts and zero when audio
     * stream setup is complete.
     */
    AUDIO_STREAMING = 9u /* ::android::hardware::power::V1_0::PowerHint.LAUNCH implicitly + 1 */,
    /**
     * This hint indicates that low latency audio is active. Can be used
     * for device specific optimizations towards low latency audio. The
     * data parameter is non-zero when low latency audio starts and
     * zero when ends.
     */
    AUDIO_LOW_LATENCY = 10u /* ::android::hardware::power::V1_2::PowerHint.AUDIO_STREAMING implicitly + 1 */,
    /**
     * These hint indicates that camera is being launched. Can be used
     * for device specific optimizations during camera launch. The data
     * parameter is non-zero when camera launch starts and zero when launch
     * is complete.
     */
    CAMERA_LAUNCH = 11u /* ::android::hardware::power::V1_2::PowerHint.AUDIO_LOW_LATENCY implicitly + 1 */,
    /**
     * This hint indicates that camera stream is being started. Can be used
     * for device specific optimizations during starting camera stream. The
     * data parameter is non-zero when stream starts and zero when ends.
     */
    CAMERA_STREAMING = 12u /* ::android::hardware::power::V1_2::PowerHint.CAMERA_LAUNCH implicitly + 1 */,
    /**
     * This hint indicates that camera shot is being taken. Can be used
     * for device specific optimizations during taking camera shot. The
     * data parameter is non-zero when camera shot starts and zero when ends.
     */
    CAMERA_SHOT = 13u /* ::android::hardware::power::V1_2::PowerHint.CAMERA_STREAMING implicitly + 1 */,
    /**
     * This hint indicates that the device is about to enter a period of expensive rendering, and
     * the GPU should be configured accordingly. The data parameter is always 1 when entering this
     * state and 0 when leaving it.
     */
    EXPENSIVE_RENDERING = 14u /* ::android::hardware::power::V1_2::PowerHint.CAMERA_SHOT implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::power::V1_3::PowerHint o);
static inline void PrintTo(::android::hardware::power::V1_3::PowerHint o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::power::V1_3::PowerHint lhs, const ::android::hardware::power::V1_3::PowerHint rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::power::V1_3::PowerHint rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::power::V1_3::PowerHint lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::power::V1_3::PowerHint lhs, const ::android::hardware::power::V1_3::PowerHint rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::power::V1_3::PowerHint rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::power::V1_3::PowerHint lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::power::V1_3::PowerHint e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::power::V1_3::PowerHint e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::power::V1_3::PowerHint>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::power::V1_3::PowerHint> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::power::V1_3::PowerHint::VSYNC) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::VSYNC)) {
        os += (first ? "" : " | ");
        os += "VSYNC";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::VSYNC;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::INTERACTION) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::INTERACTION)) {
        os += (first ? "" : " | ");
        os += "INTERACTION";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::INTERACTION;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::VIDEO_ENCODE) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::VIDEO_ENCODE)) {
        os += (first ? "" : " | ");
        os += "VIDEO_ENCODE";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::VIDEO_ENCODE;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::VIDEO_DECODE) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::VIDEO_DECODE)) {
        os += (first ? "" : " | ");
        os += "VIDEO_DECODE";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::VIDEO_DECODE;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::LOW_POWER) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::LOW_POWER)) {
        os += (first ? "" : " | ");
        os += "LOW_POWER";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::LOW_POWER;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::SUSTAINED_PERFORMANCE) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::SUSTAINED_PERFORMANCE)) {
        os += (first ? "" : " | ");
        os += "SUSTAINED_PERFORMANCE";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::SUSTAINED_PERFORMANCE;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::VR_MODE) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::VR_MODE)) {
        os += (first ? "" : " | ");
        os += "VR_MODE";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::VR_MODE;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::LAUNCH) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::LAUNCH)) {
        os += (first ? "" : " | ");
        os += "LAUNCH";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::LAUNCH;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::AUDIO_STREAMING) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::AUDIO_STREAMING)) {
        os += (first ? "" : " | ");
        os += "AUDIO_STREAMING";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::AUDIO_STREAMING;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::AUDIO_LOW_LATENCY) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::AUDIO_LOW_LATENCY)) {
        os += (first ? "" : " | ");
        os += "AUDIO_LOW_LATENCY";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::AUDIO_LOW_LATENCY;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::CAMERA_LAUNCH) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::CAMERA_LAUNCH)) {
        os += (first ? "" : " | ");
        os += "CAMERA_LAUNCH";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::CAMERA_LAUNCH;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::CAMERA_STREAMING) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::CAMERA_STREAMING)) {
        os += (first ? "" : " | ");
        os += "CAMERA_STREAMING";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::CAMERA_STREAMING;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::CAMERA_SHOT) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::CAMERA_SHOT)) {
        os += (first ? "" : " | ");
        os += "CAMERA_SHOT";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::CAMERA_SHOT;
    }
    if ((o & ::android::hardware::power::V1_3::PowerHint::EXPENSIVE_RENDERING) == static_cast<uint32_t>(::android::hardware::power::V1_3::PowerHint::EXPENSIVE_RENDERING)) {
        os += (first ? "" : " | ");
        os += "EXPENSIVE_RENDERING";
        first = false;
        flipped |= ::android::hardware::power::V1_3::PowerHint::EXPENSIVE_RENDERING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::power::V1_3::PowerHint o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::power::V1_3::PowerHint::VSYNC) {
        return "VSYNC";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::INTERACTION) {
        return "INTERACTION";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::VIDEO_ENCODE) {
        return "VIDEO_ENCODE";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::VIDEO_DECODE) {
        return "VIDEO_DECODE";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::LOW_POWER) {
        return "LOW_POWER";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::SUSTAINED_PERFORMANCE) {
        return "SUSTAINED_PERFORMANCE";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::VR_MODE) {
        return "VR_MODE";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::LAUNCH) {
        return "LAUNCH";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::AUDIO_STREAMING) {
        return "AUDIO_STREAMING";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::AUDIO_LOW_LATENCY) {
        return "AUDIO_LOW_LATENCY";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::CAMERA_LAUNCH) {
        return "CAMERA_LAUNCH";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::CAMERA_STREAMING) {
        return "CAMERA_STREAMING";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::CAMERA_SHOT) {
        return "CAMERA_SHOT";
    }
    if (o == ::android::hardware::power::V1_3::PowerHint::EXPENSIVE_RENDERING) {
        return "EXPENSIVE_RENDERING";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::power::V1_3::PowerHint o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_3
}  // namespace power
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
template<> inline constexpr std::array<::android::hardware::power::V1_3::PowerHint, 14> hidl_enum_values<::android::hardware::power::V1_3::PowerHint> = {
    ::android::hardware::power::V1_3::PowerHint::VSYNC,
    ::android::hardware::power::V1_3::PowerHint::INTERACTION,
    ::android::hardware::power::V1_3::PowerHint::VIDEO_ENCODE,
    ::android::hardware::power::V1_3::PowerHint::VIDEO_DECODE,
    ::android::hardware::power::V1_3::PowerHint::LOW_POWER,
    ::android::hardware::power::V1_3::PowerHint::SUSTAINED_PERFORMANCE,
    ::android::hardware::power::V1_3::PowerHint::VR_MODE,
    ::android::hardware::power::V1_3::PowerHint::LAUNCH,
    ::android::hardware::power::V1_3::PowerHint::AUDIO_STREAMING,
    ::android::hardware::power::V1_3::PowerHint::AUDIO_LOW_LATENCY,
    ::android::hardware::power::V1_3::PowerHint::CAMERA_LAUNCH,
    ::android::hardware::power::V1_3::PowerHint::CAMERA_STREAMING,
    ::android::hardware::power::V1_3::PowerHint::CAMERA_SHOT,
    ::android::hardware::power::V1_3::PowerHint::EXPENSIVE_RENDERING,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_3_TYPES_H
