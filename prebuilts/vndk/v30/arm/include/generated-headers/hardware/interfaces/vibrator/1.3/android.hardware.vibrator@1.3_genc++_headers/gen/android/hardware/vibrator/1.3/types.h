#ifndef HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_3_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_3_TYPES_H

#include <android/hardware/vibrator/1.2/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace vibrator {
namespace V1_3 {

// Forward declaration for forward reference support:
enum class Effect : uint32_t;

enum class Effect : uint32_t {
    /**
     * A single click effect.
     *
     * This effect should produce a sharp, crisp click sensation.
     */
    CLICK = 0u,
    /**
     * A double click effect.
     *
     * This effect should produce two sequential sharp, crisp click sensations with a minimal
     * amount of time between them.
     */
    DOUBLE_CLICK = 1u /* ::android::hardware::vibrator::V1_0::Effect.CLICK implicitly + 1 */,
    /**
     * A tick effect.
     *
     * This effect should produce a soft, short sensation, like the tick of a clock.
     */
    TICK = 2u /* ::android::hardware::vibrator::V1_0::Effect.DOUBLE_CLICK implicitly + 1 */,
    /**
     * A thud effect.
     *
     * This effect should solid feeling bump, like the depression of a heavy mechanical button.
     */
    THUD = 3u /* ::android::hardware::vibrator::V1_1::Effect_1_1.TICK implicitly + 1 */,
    /**
     * A pop effect.
     *
     * A short, quick burst effect.
     */
    POP = 4u /* ::android::hardware::vibrator::V1_2::Effect.THUD implicitly + 1 */,
    /**
     * A heavy click effect.
     *
     * This should produce a sharp striking sensation, like a click but stronger.
     */
    HEAVY_CLICK = 5u /* ::android::hardware::vibrator::V1_2::Effect.POP implicitly + 1 */,
    /**
     * Ringtone patterns. They may correspond with the device's ringtone audio, or may just be a
     * pattern that can be played as a ringtone with any audio, depending on the device.
     */
    RINGTONE_1 = 6u /* ::android::hardware::vibrator::V1_2::Effect.HEAVY_CLICK implicitly + 1 */,
    RINGTONE_2 = 7u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_1 implicitly + 1 */,
    RINGTONE_3 = 8u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_2 implicitly + 1 */,
    RINGTONE_4 = 9u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_3 implicitly + 1 */,
    RINGTONE_5 = 10u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_4 implicitly + 1 */,
    RINGTONE_6 = 11u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_5 implicitly + 1 */,
    RINGTONE_7 = 12u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_6 implicitly + 1 */,
    RINGTONE_8 = 13u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_7 implicitly + 1 */,
    RINGTONE_9 = 14u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_8 implicitly + 1 */,
    RINGTONE_10 = 15u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_9 implicitly + 1 */,
    RINGTONE_11 = 16u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_10 implicitly + 1 */,
    RINGTONE_12 = 17u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_11 implicitly + 1 */,
    RINGTONE_13 = 18u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_12 implicitly + 1 */,
    RINGTONE_14 = 19u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_13 implicitly + 1 */,
    RINGTONE_15 = 20u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_14 implicitly + 1 */,
    /**
     * A soft tick effect meant to be played as a texture.
     *
     * A soft, short sensation like the tick of a clock. Unlike regular effects, texture effects
     * are expected to be played multiple times in quick succession, replicating a specific
     * texture to the user as a form of haptic feedback.
     */
    TEXTURE_TICK = 21u /* ::android::hardware::vibrator::V1_2::Effect.RINGTONE_15 implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::vibrator::V1_3::Effect o);
static inline void PrintTo(::android::hardware::vibrator::V1_3::Effect o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::vibrator::V1_3::Effect lhs, const ::android::hardware::vibrator::V1_3::Effect rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::vibrator::V1_3::Effect rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::vibrator::V1_3::Effect lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::vibrator::V1_3::Effect lhs, const ::android::hardware::vibrator::V1_3::Effect rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::vibrator::V1_3::Effect rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::vibrator::V1_3::Effect lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::vibrator::V1_3::Effect e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::vibrator::V1_3::Effect e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::vibrator::V1_3::Effect>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::vibrator::V1_3::Effect> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::vibrator::V1_3::Effect::CLICK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::CLICK)) {
        os += (first ? "" : " | ");
        os += "CLICK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::CLICK;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::DOUBLE_CLICK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::DOUBLE_CLICK)) {
        os += (first ? "" : " | ");
        os += "DOUBLE_CLICK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::DOUBLE_CLICK;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::TICK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::TICK)) {
        os += (first ? "" : " | ");
        os += "TICK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::TICK;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::THUD) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::THUD)) {
        os += (first ? "" : " | ");
        os += "THUD";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::THUD;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::POP) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::POP)) {
        os += (first ? "" : " | ");
        os += "POP";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::POP;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::HEAVY_CLICK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::HEAVY_CLICK)) {
        os += (first ? "" : " | ");
        os += "HEAVY_CLICK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::HEAVY_CLICK;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_1) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_1)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_1";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_1;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_2) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_2)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_2";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_2;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_3) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_3)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_3";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_3;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_4) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_4)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_4";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_4;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_5) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_5)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_5";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_5;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_6) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_6)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_6";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_6;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_7) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_7)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_7";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_7;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_8) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_8)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_8";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_8;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_9) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_9)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_9";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_9;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_10) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_10)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_10";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_10;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_11) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_11)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_11";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_11;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_12) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_12)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_12";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_12;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_13) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_13)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_13";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_13;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_14) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_14)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_14";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_14;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::RINGTONE_15) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::RINGTONE_15)) {
        os += (first ? "" : " | ");
        os += "RINGTONE_15";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::RINGTONE_15;
    }
    if ((o & ::android::hardware::vibrator::V1_3::Effect::TEXTURE_TICK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_3::Effect::TEXTURE_TICK)) {
        os += (first ? "" : " | ");
        os += "TEXTURE_TICK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_3::Effect::TEXTURE_TICK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::vibrator::V1_3::Effect o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::vibrator::V1_3::Effect::CLICK) {
        return "CLICK";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::DOUBLE_CLICK) {
        return "DOUBLE_CLICK";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::TICK) {
        return "TICK";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::THUD) {
        return "THUD";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::POP) {
        return "POP";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::HEAVY_CLICK) {
        return "HEAVY_CLICK";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_1) {
        return "RINGTONE_1";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_2) {
        return "RINGTONE_2";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_3) {
        return "RINGTONE_3";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_4) {
        return "RINGTONE_4";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_5) {
        return "RINGTONE_5";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_6) {
        return "RINGTONE_6";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_7) {
        return "RINGTONE_7";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_8) {
        return "RINGTONE_8";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_9) {
        return "RINGTONE_9";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_10) {
        return "RINGTONE_10";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_11) {
        return "RINGTONE_11";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_12) {
        return "RINGTONE_12";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_13) {
        return "RINGTONE_13";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_14) {
        return "RINGTONE_14";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::RINGTONE_15) {
        return "RINGTONE_15";
    }
    if (o == ::android::hardware::vibrator::V1_3::Effect::TEXTURE_TICK) {
        return "TEXTURE_TICK";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::vibrator::V1_3::Effect o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_3
}  // namespace vibrator
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
template<> inline constexpr std::array<::android::hardware::vibrator::V1_3::Effect, 22> hidl_enum_values<::android::hardware::vibrator::V1_3::Effect> = {
    ::android::hardware::vibrator::V1_3::Effect::CLICK,
    ::android::hardware::vibrator::V1_3::Effect::DOUBLE_CLICK,
    ::android::hardware::vibrator::V1_3::Effect::TICK,
    ::android::hardware::vibrator::V1_3::Effect::THUD,
    ::android::hardware::vibrator::V1_3::Effect::POP,
    ::android::hardware::vibrator::V1_3::Effect::HEAVY_CLICK,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_1,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_2,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_3,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_4,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_5,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_6,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_7,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_8,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_9,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_10,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_11,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_12,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_13,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_14,
    ::android::hardware::vibrator::V1_3::Effect::RINGTONE_15,
    ::android::hardware::vibrator::V1_3::Effect::TEXTURE_TICK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_3_TYPES_H
