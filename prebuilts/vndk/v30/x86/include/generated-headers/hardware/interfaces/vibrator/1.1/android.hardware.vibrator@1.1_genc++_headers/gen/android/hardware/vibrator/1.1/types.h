#ifndef HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_1_TYPES_H

#include <android/hardware/vibrator/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace vibrator {
namespace V1_1 {

// Forward declaration for forward reference support:
enum class Effect_1_1 : uint32_t;

enum class Effect_1_1 : uint32_t {
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
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::vibrator::V1_1::Effect_1_1 o);
static inline void PrintTo(::android::hardware::vibrator::V1_1::Effect_1_1 o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::vibrator::V1_1::Effect_1_1 lhs, const ::android::hardware::vibrator::V1_1::Effect_1_1 rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::vibrator::V1_1::Effect_1_1 rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::vibrator::V1_1::Effect_1_1 lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::vibrator::V1_1::Effect_1_1 lhs, const ::android::hardware::vibrator::V1_1::Effect_1_1 rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::vibrator::V1_1::Effect_1_1 rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::vibrator::V1_1::Effect_1_1 lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::vibrator::V1_1::Effect_1_1 e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::vibrator::V1_1::Effect_1_1 e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::vibrator::V1_1::Effect_1_1>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::vibrator::V1_1::Effect_1_1> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::vibrator::V1_1::Effect_1_1::CLICK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_1::Effect_1_1::CLICK)) {
        os += (first ? "" : " | ");
        os += "CLICK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_1::Effect_1_1::CLICK;
    }
    if ((o & ::android::hardware::vibrator::V1_1::Effect_1_1::DOUBLE_CLICK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_1::Effect_1_1::DOUBLE_CLICK)) {
        os += (first ? "" : " | ");
        os += "DOUBLE_CLICK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_1::Effect_1_1::DOUBLE_CLICK;
    }
    if ((o & ::android::hardware::vibrator::V1_1::Effect_1_1::TICK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_1::Effect_1_1::TICK)) {
        os += (first ? "" : " | ");
        os += "TICK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_1::Effect_1_1::TICK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::vibrator::V1_1::Effect_1_1 o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::vibrator::V1_1::Effect_1_1::CLICK) {
        return "CLICK";
    }
    if (o == ::android::hardware::vibrator::V1_1::Effect_1_1::DOUBLE_CLICK) {
        return "DOUBLE_CLICK";
    }
    if (o == ::android::hardware::vibrator::V1_1::Effect_1_1::TICK) {
        return "TICK";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::vibrator::V1_1::Effect_1_1 o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_1
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
template<> inline constexpr std::array<::android::hardware::vibrator::V1_1::Effect_1_1, 3> hidl_enum_values<::android::hardware::vibrator::V1_1::Effect_1_1> = {
    ::android::hardware::vibrator::V1_1::Effect_1_1::CLICK,
    ::android::hardware::vibrator::V1_1::Effect_1_1::DOUBLE_CLICK,
    ::android::hardware::vibrator::V1_1::Effect_1_1::TICK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_1_TYPES_H
