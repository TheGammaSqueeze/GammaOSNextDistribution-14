#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V2_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace audiocontrol {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class AudioFocusChange : int32_t;

/**
 * Changes in audio focus that can be experienced
 */
enum class AudioFocusChange : int32_t {
    NONE = 0,
    GAIN = 1,
    GAIN_TRANSIENT = 2,
    GAIN_TRANSIENT_MAY_DUCK = 3,
    GAIN_TRANSIENT_EXCLUSIVE = 4,
    LOSS = -1 /* -1 * GAIN */,
    LOSS_TRANSIENT = -2 /* -1 * GAIN_TRANSIENT */,
    LOSS_TRANSIENT_CAN_DUCK = -3 /* -1 * GAIN_TRANSIENT_MAY_DUCK */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange o);
static inline void PrintTo(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange lhs, const ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange lhs, const ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange e) {
    v &= static_cast<int32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::NONE) == static_cast<int32_t>(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::NONE;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN) == static_cast<int32_t>(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN)) {
        os += (first ? "" : " | ");
        os += "GAIN";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT) == static_cast<int32_t>(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT)) {
        os += (first ? "" : " | ");
        os += "GAIN_TRANSIENT";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT_MAY_DUCK) == static_cast<int32_t>(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT_MAY_DUCK)) {
        os += (first ? "" : " | ");
        os += "GAIN_TRANSIENT_MAY_DUCK";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT_MAY_DUCK;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT_EXCLUSIVE) == static_cast<int32_t>(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT_EXCLUSIVE)) {
        os += (first ? "" : " | ");
        os += "GAIN_TRANSIENT_EXCLUSIVE";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT_EXCLUSIVE;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS) == static_cast<int32_t>(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS)) {
        os += (first ? "" : " | ");
        os += "LOSS";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS_TRANSIENT) == static_cast<int32_t>(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS_TRANSIENT)) {
        os += (first ? "" : " | ");
        os += "LOSS_TRANSIENT";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS_TRANSIENT;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS_TRANSIENT_CAN_DUCK) == static_cast<int32_t>(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS_TRANSIENT_CAN_DUCK)) {
        os += (first ? "" : " | ");
        os += "LOSS_TRANSIENT_CAN_DUCK";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS_TRANSIENT_CAN_DUCK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN) {
        return "GAIN";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT) {
        return "GAIN_TRANSIENT";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT_MAY_DUCK) {
        return "GAIN_TRANSIENT_MAY_DUCK";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT_EXCLUSIVE) {
        return "GAIN_TRANSIENT_EXCLUSIVE";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS) {
        return "LOSS";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS_TRANSIENT) {
        return "LOSS_TRANSIENT";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS_TRANSIENT_CAN_DUCK) {
        return "LOSS_TRANSIENT_CAN_DUCK";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V2_0
}  // namespace audiocontrol
}  // namespace automotive
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
template<> inline constexpr std::array<::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange, 8> hidl_enum_values<::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange> = {
    ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::NONE,
    ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN,
    ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT,
    ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT_MAY_DUCK,
    ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::GAIN_TRANSIENT_EXCLUSIVE,
    ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS,
    ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS_TRANSIENT,
    ::android::hardware::automotive::audiocontrol::V2_0::AudioFocusChange::LOSS_TRANSIENT_CAN_DUCK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V2_0_TYPES_H
