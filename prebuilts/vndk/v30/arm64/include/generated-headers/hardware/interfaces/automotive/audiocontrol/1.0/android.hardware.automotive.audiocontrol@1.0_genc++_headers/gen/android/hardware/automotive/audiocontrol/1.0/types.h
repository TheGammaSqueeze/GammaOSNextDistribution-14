#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace audiocontrol {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class ContextNumber : uint32_t;

/**
 * Predefined flags to identifying audio contexts
 */
enum class ContextNumber : uint32_t {
    INVALID = 0u,
    /*
     * Shouldn't be used
     */
    MUSIC = 1u /* ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.INVALID implicitly + 1 */,
    /*
     * Music playback
     */
    NAVIGATION = 2u /* ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.MUSIC implicitly + 1 */,
    /*
     * Navigation directions
     */
    VOICE_COMMAND = 3u /* ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.NAVIGATION implicitly + 1 */,
    /*
     * Voice command session
     */
    CALL_RING = 4u /* ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.VOICE_COMMAND implicitly + 1 */,
    /*
     * Voice call ringing
     */
    CALL = 5u /* ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.CALL_RING implicitly + 1 */,
    /*
     * Voice call
     */
    ALARM = 6u /* ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.CALL implicitly + 1 */,
    /*
     * Alarm sound from Android
     */
    NOTIFICATION = 7u /* ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.ALARM implicitly + 1 */,
    /*
     * Notifications
     */
    SYSTEM_SOUND = 8u /* ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.NOTIFICATION implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber o);
static inline void PrintTo(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber lhs, const ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber lhs, const ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::automotive::audiocontrol::V1_0::ContextNumber>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::audiocontrol::V1_0::ContextNumber> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::INVALID) == static_cast<uint32_t>(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::INVALID;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::MUSIC) == static_cast<uint32_t>(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::MUSIC)) {
        os += (first ? "" : " | ");
        os += "MUSIC";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::MUSIC;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::NAVIGATION) == static_cast<uint32_t>(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::NAVIGATION)) {
        os += (first ? "" : " | ");
        os += "NAVIGATION";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::NAVIGATION;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::VOICE_COMMAND) == static_cast<uint32_t>(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::VOICE_COMMAND)) {
        os += (first ? "" : " | ");
        os += "VOICE_COMMAND";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::VOICE_COMMAND;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::CALL_RING) == static_cast<uint32_t>(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::CALL_RING)) {
        os += (first ? "" : " | ");
        os += "CALL_RING";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::CALL_RING;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::CALL) == static_cast<uint32_t>(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::CALL)) {
        os += (first ? "" : " | ");
        os += "CALL";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::CALL;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::ALARM) == static_cast<uint32_t>(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::ALARM)) {
        os += (first ? "" : " | ");
        os += "ALARM";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::ALARM;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::NOTIFICATION) == static_cast<uint32_t>(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::NOTIFICATION)) {
        os += (first ? "" : " | ");
        os += "NOTIFICATION";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::NOTIFICATION;
    }
    if ((o & ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::SYSTEM_SOUND) == static_cast<uint32_t>(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::SYSTEM_SOUND)) {
        os += (first ? "" : " | ");
        os += "SYSTEM_SOUND";
        first = false;
        flipped |= ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::SYSTEM_SOUND;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::MUSIC) {
        return "MUSIC";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::NAVIGATION) {
        return "NAVIGATION";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::VOICE_COMMAND) {
        return "VOICE_COMMAND";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::CALL_RING) {
        return "CALL_RING";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::CALL) {
        return "CALL";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::ALARM) {
        return "ALARM";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::NOTIFICATION) {
        return "NOTIFICATION";
    }
    if (o == ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::SYSTEM_SOUND) {
        return "SYSTEM_SOUND";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::audiocontrol::V1_0::ContextNumber o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
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
template<> inline constexpr std::array<::android::hardware::automotive::audiocontrol::V1_0::ContextNumber, 9> hidl_enum_values<::android::hardware::automotive::audiocontrol::V1_0::ContextNumber> = {
    ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::INVALID,
    ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::MUSIC,
    ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::NAVIGATION,
    ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::VOICE_COMMAND,
    ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::CALL_RING,
    ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::CALL,
    ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::ALARM,
    ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::NOTIFICATION,
    ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber::SYSTEM_SOUND,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V1_0_TYPES_H
