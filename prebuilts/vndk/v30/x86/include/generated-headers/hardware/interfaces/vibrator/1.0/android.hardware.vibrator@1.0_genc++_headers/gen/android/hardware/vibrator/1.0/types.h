#ifndef HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace vibrator {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;
enum class Effect : uint32_t;
enum class EffectStrength : uint8_t;

enum class Status : uint32_t {
    OK = 0u,
    UNKNOWN_ERROR = 1u /* ::android::hardware::vibrator::V1_0::Status.OK implicitly + 1 */,
    BAD_VALUE = 2u /* ::android::hardware::vibrator::V1_0::Status.UNKNOWN_ERROR implicitly + 1 */,
    UNSUPPORTED_OPERATION = 3u /* ::android::hardware::vibrator::V1_0::Status.BAD_VALUE implicitly + 1 */,
};

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
};

enum class EffectStrength : uint8_t {
    LIGHT = 0,
    MEDIUM = 1 /* ::android::hardware::vibrator::V1_0::EffectStrength.LIGHT implicitly + 1 */,
    STRONG = 2 /* ::android::hardware::vibrator::V1_0::EffectStrength.MEDIUM implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::vibrator::V1_0::Status o);
static inline void PrintTo(::android::hardware::vibrator::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::vibrator::V1_0::Status lhs, const ::android::hardware::vibrator::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::vibrator::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::vibrator::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::vibrator::V1_0::Status lhs, const ::android::hardware::vibrator::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::vibrator::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::vibrator::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::vibrator::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::vibrator::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::vibrator::V1_0::Effect o);
static inline void PrintTo(::android::hardware::vibrator::V1_0::Effect o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::vibrator::V1_0::Effect lhs, const ::android::hardware::vibrator::V1_0::Effect rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::vibrator::V1_0::Effect rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::vibrator::V1_0::Effect lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::vibrator::V1_0::Effect lhs, const ::android::hardware::vibrator::V1_0::Effect rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::vibrator::V1_0::Effect rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::vibrator::V1_0::Effect lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::vibrator::V1_0::Effect e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::vibrator::V1_0::Effect e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::vibrator::V1_0::EffectStrength o);
static inline void PrintTo(::android::hardware::vibrator::V1_0::EffectStrength o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::vibrator::V1_0::EffectStrength lhs, const ::android::hardware::vibrator::V1_0::EffectStrength rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::vibrator::V1_0::EffectStrength rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::vibrator::V1_0::EffectStrength lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::vibrator::V1_0::EffectStrength lhs, const ::android::hardware::vibrator::V1_0::EffectStrength rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::vibrator::V1_0::EffectStrength rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::vibrator::V1_0::EffectStrength lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::vibrator::V1_0::EffectStrength e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::vibrator::V1_0::EffectStrength e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::vibrator::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::vibrator::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::vibrator::V1_0::Status::OK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_0::Status::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_0::Status::OK;
    }
    if ((o & ::android::hardware::vibrator::V1_0::Status::UNKNOWN_ERROR) == static_cast<uint32_t>(::android::hardware::vibrator::V1_0::Status::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_0::Status::UNKNOWN_ERROR;
    }
    if ((o & ::android::hardware::vibrator::V1_0::Status::BAD_VALUE) == static_cast<uint32_t>(::android::hardware::vibrator::V1_0::Status::BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "BAD_VALUE";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_0::Status::BAD_VALUE;
    }
    if ((o & ::android::hardware::vibrator::V1_0::Status::UNSUPPORTED_OPERATION) == static_cast<uint32_t>(::android::hardware::vibrator::V1_0::Status::UNSUPPORTED_OPERATION)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_OPERATION";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_0::Status::UNSUPPORTED_OPERATION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::vibrator::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::vibrator::V1_0::Status::OK) {
        return "OK";
    }
    if (o == ::android::hardware::vibrator::V1_0::Status::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    if (o == ::android::hardware::vibrator::V1_0::Status::BAD_VALUE) {
        return "BAD_VALUE";
    }
    if (o == ::android::hardware::vibrator::V1_0::Status::UNSUPPORTED_OPERATION) {
        return "UNSUPPORTED_OPERATION";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::vibrator::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::vibrator::V1_0::Effect>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::vibrator::V1_0::Effect> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::vibrator::V1_0::Effect::CLICK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_0::Effect::CLICK)) {
        os += (first ? "" : " | ");
        os += "CLICK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_0::Effect::CLICK;
    }
    if ((o & ::android::hardware::vibrator::V1_0::Effect::DOUBLE_CLICK) == static_cast<uint32_t>(::android::hardware::vibrator::V1_0::Effect::DOUBLE_CLICK)) {
        os += (first ? "" : " | ");
        os += "DOUBLE_CLICK";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_0::Effect::DOUBLE_CLICK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::vibrator::V1_0::Effect o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::vibrator::V1_0::Effect::CLICK) {
        return "CLICK";
    }
    if (o == ::android::hardware::vibrator::V1_0::Effect::DOUBLE_CLICK) {
        return "DOUBLE_CLICK";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::vibrator::V1_0::Effect o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::vibrator::V1_0::EffectStrength>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::vibrator::V1_0::EffectStrength> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::vibrator::V1_0::EffectStrength::LIGHT) == static_cast<uint8_t>(::android::hardware::vibrator::V1_0::EffectStrength::LIGHT)) {
        os += (first ? "" : " | ");
        os += "LIGHT";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_0::EffectStrength::LIGHT;
    }
    if ((o & ::android::hardware::vibrator::V1_0::EffectStrength::MEDIUM) == static_cast<uint8_t>(::android::hardware::vibrator::V1_0::EffectStrength::MEDIUM)) {
        os += (first ? "" : " | ");
        os += "MEDIUM";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_0::EffectStrength::MEDIUM;
    }
    if ((o & ::android::hardware::vibrator::V1_0::EffectStrength::STRONG) == static_cast<uint8_t>(::android::hardware::vibrator::V1_0::EffectStrength::STRONG)) {
        os += (first ? "" : " | ");
        os += "STRONG";
        first = false;
        flipped |= ::android::hardware::vibrator::V1_0::EffectStrength::STRONG;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::vibrator::V1_0::EffectStrength o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::vibrator::V1_0::EffectStrength::LIGHT) {
        return "LIGHT";
    }
    if (o == ::android::hardware::vibrator::V1_0::EffectStrength::MEDIUM) {
        return "MEDIUM";
    }
    if (o == ::android::hardware::vibrator::V1_0::EffectStrength::STRONG) {
        return "STRONG";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::vibrator::V1_0::EffectStrength o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
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
template<> inline constexpr std::array<::android::hardware::vibrator::V1_0::Status, 4> hidl_enum_values<::android::hardware::vibrator::V1_0::Status> = {
    ::android::hardware::vibrator::V1_0::Status::OK,
    ::android::hardware::vibrator::V1_0::Status::UNKNOWN_ERROR,
    ::android::hardware::vibrator::V1_0::Status::BAD_VALUE,
    ::android::hardware::vibrator::V1_0::Status::UNSUPPORTED_OPERATION,
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
template<> inline constexpr std::array<::android::hardware::vibrator::V1_0::Effect, 2> hidl_enum_values<::android::hardware::vibrator::V1_0::Effect> = {
    ::android::hardware::vibrator::V1_0::Effect::CLICK,
    ::android::hardware::vibrator::V1_0::Effect::DOUBLE_CLICK,
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
template<> inline constexpr std::array<::android::hardware::vibrator::V1_0::EffectStrength, 3> hidl_enum_values<::android::hardware::vibrator::V1_0::EffectStrength> = {
    ::android::hardware::vibrator::V1_0::EffectStrength::LIGHT,
    ::android::hardware::vibrator::V1_0::EffectStrength::MEDIUM,
    ::android::hardware::vibrator::V1_0::EffectStrength::STRONG,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_0_TYPES_H
