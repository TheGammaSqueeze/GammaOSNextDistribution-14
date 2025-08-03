#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_1_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_1 {

// Forward declaration for forward reference support:
enum class Setting : uint8_t;
enum class SettingValue : uint8_t;

/**
 * Used to indicate the type of user setting that has changed.
 */
enum class Setting : uint8_t {
    LOCATION = 0,
};

/**
 * Used to indicate the value of a user setting.
 */
enum class SettingValue : uint8_t {
    DISABLED = 0,
    ENABLED = 1 /* ::android::hardware::contexthub::V1_1::SettingValue.DISABLED implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::contexthub::V1_1::Setting o);
static inline void PrintTo(::android::hardware::contexthub::V1_1::Setting o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::contexthub::V1_1::Setting lhs, const ::android::hardware::contexthub::V1_1::Setting rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::contexthub::V1_1::Setting rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::contexthub::V1_1::Setting lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::contexthub::V1_1::Setting lhs, const ::android::hardware::contexthub::V1_1::Setting rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::contexthub::V1_1::Setting rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::contexthub::V1_1::Setting lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::contexthub::V1_1::Setting e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::contexthub::V1_1::Setting e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::contexthub::V1_1::SettingValue o);
static inline void PrintTo(::android::hardware::contexthub::V1_1::SettingValue o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::contexthub::V1_1::SettingValue lhs, const ::android::hardware::contexthub::V1_1::SettingValue rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::contexthub::V1_1::SettingValue rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::contexthub::V1_1::SettingValue lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::contexthub::V1_1::SettingValue lhs, const ::android::hardware::contexthub::V1_1::SettingValue rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::contexthub::V1_1::SettingValue rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::contexthub::V1_1::SettingValue lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::contexthub::V1_1::SettingValue e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::contexthub::V1_1::SettingValue e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::contexthub::V1_1::Setting>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_1::Setting> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::contexthub::V1_1::Setting::LOCATION) == static_cast<uint8_t>(::android::hardware::contexthub::V1_1::Setting::LOCATION)) {
        os += (first ? "" : " | ");
        os += "LOCATION";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_1::Setting::LOCATION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::contexthub::V1_1::Setting o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::contexthub::V1_1::Setting::LOCATION) {
        return "LOCATION";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::contexthub::V1_1::Setting o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::contexthub::V1_1::SettingValue>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_1::SettingValue> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::contexthub::V1_1::SettingValue::DISABLED) == static_cast<uint8_t>(::android::hardware::contexthub::V1_1::SettingValue::DISABLED)) {
        os += (first ? "" : " | ");
        os += "DISABLED";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_1::SettingValue::DISABLED;
    }
    if ((o & ::android::hardware::contexthub::V1_1::SettingValue::ENABLED) == static_cast<uint8_t>(::android::hardware::contexthub::V1_1::SettingValue::ENABLED)) {
        os += (first ? "" : " | ");
        os += "ENABLED";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_1::SettingValue::ENABLED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::contexthub::V1_1::SettingValue o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::contexthub::V1_1::SettingValue::DISABLED) {
        return "DISABLED";
    }
    if (o == ::android::hardware::contexthub::V1_1::SettingValue::ENABLED) {
        return "ENABLED";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::contexthub::V1_1::SettingValue o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_1
}  // namespace contexthub
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
template<> inline constexpr std::array<::android::hardware::contexthub::V1_1::Setting, 1> hidl_enum_values<::android::hardware::contexthub::V1_1::Setting> = {
    ::android::hardware::contexthub::V1_1::Setting::LOCATION,
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
template<> inline constexpr std::array<::android::hardware::contexthub::V1_1::SettingValue, 2> hidl_enum_values<::android::hardware::contexthub::V1_1::SettingValue> = {
    ::android::hardware::contexthub::V1_1::SettingValue::DISABLED,
    ::android::hardware::contexthub::V1_1::SettingValue::ENABLED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_1_TYPES_H
