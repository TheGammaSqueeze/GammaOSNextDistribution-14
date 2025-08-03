#ifndef HIDL_GENERATED_ANDROID_HARDWARE_LIGHT_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_LIGHT_V2_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace light {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class Status : int32_t;
enum class Flash : int32_t;
enum class Brightness : int32_t;
enum class Type : int32_t;
struct LightState;

enum class Status : int32_t {
    SUCCESS = 0,
    LIGHT_NOT_SUPPORTED = 1 /* ::android::hardware::light::V2_0::Status.SUCCESS implicitly + 1 */,
    BRIGHTNESS_NOT_SUPPORTED = 2 /* ::android::hardware::light::V2_0::Status.LIGHT_NOT_SUPPORTED implicitly + 1 */,
    UNKNOWN = 3 /* ::android::hardware::light::V2_0::Status.BRIGHTNESS_NOT_SUPPORTED implicitly + 1 */,
};

enum class Flash : int32_t {
    /**
     * Keep the light steady on or off.
     */
    NONE = 0,
    /**
     * Flash the light at specified rate.
     */
    TIMED = 1 /* ::android::hardware::light::V2_0::Flash.NONE implicitly + 1 */,
    /**
     * Flash the light using hardware assist.
     */
    HARDWARE = 2 /* ::android::hardware::light::V2_0::Flash.TIMED implicitly + 1 */,
};

enum class Brightness : int32_t {
    /**
     * Light brightness is managed by a user setting.
     */
    USER = 0,
    /**
     * Light brightness is managed by a light sensor.
     */
    SENSOR = 1 /* ::android::hardware::light::V2_0::Brightness.USER implicitly + 1 */,
    /**
     * Use a low-persistence mode for display backlights.
     *
     * When set, the device driver must switch to a mode optimized for low display
     * persistence that is intended to be used when the device is being treated as a
     * head mounted display (HMD). The actual display brightness in this mode is
     * implementation dependent, and any value set for color in LightState may be
     * overridden by the HAL implementation.
     *
     * For an optimal HMD viewing experience, the display must meet the following
     * criteria in this mode:
     * - Gray-to-Gray, White-to-Black, and Black-to-White switching time must be ≤ 3 ms.
     * - The display must support low-persistence with ≤ 3.5 ms persistence.
     *   Persistence is defined as the amount of time for which a pixel is
     *   emitting light for a single frame.
     * - Any "smart panel" or other frame buffering options that increase display
     *   latency are disabled.
     * - Display brightness is set so that the display is still visible to the user
     *   under normal indoor lighting.
     * - The display must update at 60 Hz at least, but higher refresh rates are
     *   recommended for low latency.
     *
     */
    LOW_PERSISTENCE = 2 /* ::android::hardware::light::V2_0::Brightness.SENSOR implicitly + 1 */,
};

/**
 * These light IDs correspond to logical lights, not physical.
 * So for example, if your INDICATOR light is in line with your
 * BUTTONS, it might make sense to also light the INDICATOR
 * light to a reasonable color when the BUTTONS are lit.
 */
enum class Type : int32_t {
    BACKLIGHT = 0,
    KEYBOARD = 1 /* ::android::hardware::light::V2_0::Type.BACKLIGHT implicitly + 1 */,
    BUTTONS = 2 /* ::android::hardware::light::V2_0::Type.KEYBOARD implicitly + 1 */,
    BATTERY = 3 /* ::android::hardware::light::V2_0::Type.BUTTONS implicitly + 1 */,
    NOTIFICATIONS = 4 /* ::android::hardware::light::V2_0::Type.BATTERY implicitly + 1 */,
    ATTENTION = 5 /* ::android::hardware::light::V2_0::Type.NOTIFICATIONS implicitly + 1 */,
    BLUETOOTH = 6 /* ::android::hardware::light::V2_0::Type.ATTENTION implicitly + 1 */,
    WIFI = 7 /* ::android::hardware::light::V2_0::Type.BLUETOOTH implicitly + 1 */,
    COUNT = 8 /* ::android::hardware::light::V2_0::Type.WIFI implicitly + 1 */,
};

/**
 * The parameters that can be set for a given light.
 *
 * Not all lights must support all parameters. If you
 * can do something backward-compatible, do it.
 */
struct LightState final {
    /**
     * The color of the LED in ARGB.
     *
     * Do your best here.
     *   - If your light can only do red or green, if they ask for blue,
     *     you should do green.
     *   - If you can only do a brightness ramp, then use this formula:
     *      unsigned char brightness = ((77*((color>>16)&0x00ff))
     *              + (150*((color>>8)&0x00ff)) + (29*(color&0x00ff))) >> 8;
     *   - If you can only do on or off, 0 is off, anything else is on.
     *
     * The high byte should be ignored. Callers will set it to 0xff (which
     * would correspond to 255 alpha).
     */
    uint32_t color __attribute__ ((aligned(4)));
    /**
     * To flash the light at a given rate, set flashMode to LIGHT_FLASH_TIMED,
     * and then flashOnMS should be set to the number of milliseconds to turn
     * the light on, followed by the number of milliseconds to turn the light
     * off.
     */
    ::android::hardware::light::V2_0::Flash flashMode __attribute__ ((aligned(4)));
    int32_t flashOnMs __attribute__ ((aligned(4)));
    int32_t flashOffMs __attribute__ ((aligned(4)));
    ::android::hardware::light::V2_0::Brightness brightnessMode __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::light::V2_0::LightState, color) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::light::V2_0::LightState, flashMode) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::light::V2_0::LightState, flashOnMs) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::light::V2_0::LightState, flashOffMs) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::light::V2_0::LightState, brightnessMode) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::light::V2_0::LightState) == 20, "wrong size");
static_assert(__alignof(::android::hardware::light::V2_0::LightState) == 4, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::light::V2_0::Status o);
static inline void PrintTo(::android::hardware::light::V2_0::Status o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::light::V2_0::Status lhs, const ::android::hardware::light::V2_0::Status rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::light::V2_0::Status rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::light::V2_0::Status lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::light::V2_0::Status lhs, const ::android::hardware::light::V2_0::Status rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::light::V2_0::Status rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::light::V2_0::Status lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::light::V2_0::Status e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::light::V2_0::Status e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::light::V2_0::Flash o);
static inline void PrintTo(::android::hardware::light::V2_0::Flash o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::light::V2_0::Flash lhs, const ::android::hardware::light::V2_0::Flash rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::light::V2_0::Flash rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::light::V2_0::Flash lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::light::V2_0::Flash lhs, const ::android::hardware::light::V2_0::Flash rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::light::V2_0::Flash rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::light::V2_0::Flash lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::light::V2_0::Flash e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::light::V2_0::Flash e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::light::V2_0::Brightness o);
static inline void PrintTo(::android::hardware::light::V2_0::Brightness o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::light::V2_0::Brightness lhs, const ::android::hardware::light::V2_0::Brightness rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::light::V2_0::Brightness rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::light::V2_0::Brightness lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::light::V2_0::Brightness lhs, const ::android::hardware::light::V2_0::Brightness rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::light::V2_0::Brightness rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::light::V2_0::Brightness lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::light::V2_0::Brightness e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::light::V2_0::Brightness e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::light::V2_0::Type o);
static inline void PrintTo(::android::hardware::light::V2_0::Type o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::light::V2_0::Type lhs, const ::android::hardware::light::V2_0::Type rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::light::V2_0::Type rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::light::V2_0::Type lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::light::V2_0::Type lhs, const ::android::hardware::light::V2_0::Type rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::light::V2_0::Type rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::light::V2_0::Type lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::light::V2_0::Type e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::light::V2_0::Type e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::light::V2_0::LightState& o);
static inline void PrintTo(const ::android::hardware::light::V2_0::LightState& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::light::V2_0::LightState& lhs, const ::android::hardware::light::V2_0::LightState& rhs);
static inline bool operator!=(const ::android::hardware::light::V2_0::LightState& lhs, const ::android::hardware::light::V2_0::LightState& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::light::V2_0::Status>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::light::V2_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::light::V2_0::Status::SUCCESS) == static_cast<int32_t>(::android::hardware::light::V2_0::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Status::SUCCESS;
    }
    if ((o & ::android::hardware::light::V2_0::Status::LIGHT_NOT_SUPPORTED) == static_cast<int32_t>(::android::hardware::light::V2_0::Status::LIGHT_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "LIGHT_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Status::LIGHT_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::light::V2_0::Status::BRIGHTNESS_NOT_SUPPORTED) == static_cast<int32_t>(::android::hardware::light::V2_0::Status::BRIGHTNESS_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "BRIGHTNESS_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Status::BRIGHTNESS_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::light::V2_0::Status::UNKNOWN) == static_cast<int32_t>(::android::hardware::light::V2_0::Status::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Status::UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::light::V2_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::light::V2_0::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::light::V2_0::Status::LIGHT_NOT_SUPPORTED) {
        return "LIGHT_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::light::V2_0::Status::BRIGHTNESS_NOT_SUPPORTED) {
        return "BRIGHTNESS_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::light::V2_0::Status::UNKNOWN) {
        return "UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::light::V2_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::light::V2_0::Flash>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::light::V2_0::Flash> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::light::V2_0::Flash::NONE) == static_cast<int32_t>(::android::hardware::light::V2_0::Flash::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Flash::NONE;
    }
    if ((o & ::android::hardware::light::V2_0::Flash::TIMED) == static_cast<int32_t>(::android::hardware::light::V2_0::Flash::TIMED)) {
        os += (first ? "" : " | ");
        os += "TIMED";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Flash::TIMED;
    }
    if ((o & ::android::hardware::light::V2_0::Flash::HARDWARE) == static_cast<int32_t>(::android::hardware::light::V2_0::Flash::HARDWARE)) {
        os += (first ? "" : " | ");
        os += "HARDWARE";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Flash::HARDWARE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::light::V2_0::Flash o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::light::V2_0::Flash::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::light::V2_0::Flash::TIMED) {
        return "TIMED";
    }
    if (o == ::android::hardware::light::V2_0::Flash::HARDWARE) {
        return "HARDWARE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::light::V2_0::Flash o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::light::V2_0::Brightness>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::light::V2_0::Brightness> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::light::V2_0::Brightness::USER) == static_cast<int32_t>(::android::hardware::light::V2_0::Brightness::USER)) {
        os += (first ? "" : " | ");
        os += "USER";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Brightness::USER;
    }
    if ((o & ::android::hardware::light::V2_0::Brightness::SENSOR) == static_cast<int32_t>(::android::hardware::light::V2_0::Brightness::SENSOR)) {
        os += (first ? "" : " | ");
        os += "SENSOR";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Brightness::SENSOR;
    }
    if ((o & ::android::hardware::light::V2_0::Brightness::LOW_PERSISTENCE) == static_cast<int32_t>(::android::hardware::light::V2_0::Brightness::LOW_PERSISTENCE)) {
        os += (first ? "" : " | ");
        os += "LOW_PERSISTENCE";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Brightness::LOW_PERSISTENCE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::light::V2_0::Brightness o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::light::V2_0::Brightness::USER) {
        return "USER";
    }
    if (o == ::android::hardware::light::V2_0::Brightness::SENSOR) {
        return "SENSOR";
    }
    if (o == ::android::hardware::light::V2_0::Brightness::LOW_PERSISTENCE) {
        return "LOW_PERSISTENCE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::light::V2_0::Brightness o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::light::V2_0::Type>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::light::V2_0::Type> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::light::V2_0::Type::BACKLIGHT) == static_cast<int32_t>(::android::hardware::light::V2_0::Type::BACKLIGHT)) {
        os += (first ? "" : " | ");
        os += "BACKLIGHT";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Type::BACKLIGHT;
    }
    if ((o & ::android::hardware::light::V2_0::Type::KEYBOARD) == static_cast<int32_t>(::android::hardware::light::V2_0::Type::KEYBOARD)) {
        os += (first ? "" : " | ");
        os += "KEYBOARD";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Type::KEYBOARD;
    }
    if ((o & ::android::hardware::light::V2_0::Type::BUTTONS) == static_cast<int32_t>(::android::hardware::light::V2_0::Type::BUTTONS)) {
        os += (first ? "" : " | ");
        os += "BUTTONS";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Type::BUTTONS;
    }
    if ((o & ::android::hardware::light::V2_0::Type::BATTERY) == static_cast<int32_t>(::android::hardware::light::V2_0::Type::BATTERY)) {
        os += (first ? "" : " | ");
        os += "BATTERY";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Type::BATTERY;
    }
    if ((o & ::android::hardware::light::V2_0::Type::NOTIFICATIONS) == static_cast<int32_t>(::android::hardware::light::V2_0::Type::NOTIFICATIONS)) {
        os += (first ? "" : " | ");
        os += "NOTIFICATIONS";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Type::NOTIFICATIONS;
    }
    if ((o & ::android::hardware::light::V2_0::Type::ATTENTION) == static_cast<int32_t>(::android::hardware::light::V2_0::Type::ATTENTION)) {
        os += (first ? "" : " | ");
        os += "ATTENTION";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Type::ATTENTION;
    }
    if ((o & ::android::hardware::light::V2_0::Type::BLUETOOTH) == static_cast<int32_t>(::android::hardware::light::V2_0::Type::BLUETOOTH)) {
        os += (first ? "" : " | ");
        os += "BLUETOOTH";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Type::BLUETOOTH;
    }
    if ((o & ::android::hardware::light::V2_0::Type::WIFI) == static_cast<int32_t>(::android::hardware::light::V2_0::Type::WIFI)) {
        os += (first ? "" : " | ");
        os += "WIFI";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Type::WIFI;
    }
    if ((o & ::android::hardware::light::V2_0::Type::COUNT) == static_cast<int32_t>(::android::hardware::light::V2_0::Type::COUNT)) {
        os += (first ? "" : " | ");
        os += "COUNT";
        first = false;
        flipped |= ::android::hardware::light::V2_0::Type::COUNT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::light::V2_0::Type o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::light::V2_0::Type::BACKLIGHT) {
        return "BACKLIGHT";
    }
    if (o == ::android::hardware::light::V2_0::Type::KEYBOARD) {
        return "KEYBOARD";
    }
    if (o == ::android::hardware::light::V2_0::Type::BUTTONS) {
        return "BUTTONS";
    }
    if (o == ::android::hardware::light::V2_0::Type::BATTERY) {
        return "BATTERY";
    }
    if (o == ::android::hardware::light::V2_0::Type::NOTIFICATIONS) {
        return "NOTIFICATIONS";
    }
    if (o == ::android::hardware::light::V2_0::Type::ATTENTION) {
        return "ATTENTION";
    }
    if (o == ::android::hardware::light::V2_0::Type::BLUETOOTH) {
        return "BLUETOOTH";
    }
    if (o == ::android::hardware::light::V2_0::Type::WIFI) {
        return "WIFI";
    }
    if (o == ::android::hardware::light::V2_0::Type::COUNT) {
        return "COUNT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::light::V2_0::Type o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::light::V2_0::LightState& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".color = ";
    os += ::android::hardware::toString(o.color);
    os += ", .flashMode = ";
    os += ::android::hardware::light::V2_0::toString(o.flashMode);
    os += ", .flashOnMs = ";
    os += ::android::hardware::toString(o.flashOnMs);
    os += ", .flashOffMs = ";
    os += ::android::hardware::toString(o.flashOffMs);
    os += ", .brightnessMode = ";
    os += ::android::hardware::light::V2_0::toString(o.brightnessMode);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::light::V2_0::LightState& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::light::V2_0::LightState& lhs, const ::android::hardware::light::V2_0::LightState& rhs) {
    if (lhs.color != rhs.color) {
        return false;
    }
    if (lhs.flashMode != rhs.flashMode) {
        return false;
    }
    if (lhs.flashOnMs != rhs.flashOnMs) {
        return false;
    }
    if (lhs.flashOffMs != rhs.flashOffMs) {
        return false;
    }
    if (lhs.brightnessMode != rhs.brightnessMode) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::light::V2_0::LightState& lhs, const ::android::hardware::light::V2_0::LightState& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_0
}  // namespace light
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
template<> inline constexpr std::array<::android::hardware::light::V2_0::Status, 4> hidl_enum_values<::android::hardware::light::V2_0::Status> = {
    ::android::hardware::light::V2_0::Status::SUCCESS,
    ::android::hardware::light::V2_0::Status::LIGHT_NOT_SUPPORTED,
    ::android::hardware::light::V2_0::Status::BRIGHTNESS_NOT_SUPPORTED,
    ::android::hardware::light::V2_0::Status::UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::light::V2_0::Flash, 3> hidl_enum_values<::android::hardware::light::V2_0::Flash> = {
    ::android::hardware::light::V2_0::Flash::NONE,
    ::android::hardware::light::V2_0::Flash::TIMED,
    ::android::hardware::light::V2_0::Flash::HARDWARE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::light::V2_0::Brightness, 3> hidl_enum_values<::android::hardware::light::V2_0::Brightness> = {
    ::android::hardware::light::V2_0::Brightness::USER,
    ::android::hardware::light::V2_0::Brightness::SENSOR,
    ::android::hardware::light::V2_0::Brightness::LOW_PERSISTENCE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::light::V2_0::Type, 9> hidl_enum_values<::android::hardware::light::V2_0::Type> = {
    ::android::hardware::light::V2_0::Type::BACKLIGHT,
    ::android::hardware::light::V2_0::Type::KEYBOARD,
    ::android::hardware::light::V2_0::Type::BUTTONS,
    ::android::hardware::light::V2_0::Type::BATTERY,
    ::android::hardware::light::V2_0::Type::NOTIFICATIONS,
    ::android::hardware::light::V2_0::Type::ATTENTION,
    ::android::hardware::light::V2_0::Type::BLUETOOTH,
    ::android::hardware::light::V2_0::Type::WIFI,
    ::android::hardware::light::V2_0::Type::COUNT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_LIGHT_V2_0_TYPES_H
