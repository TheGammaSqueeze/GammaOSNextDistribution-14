#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONFIGSTORE_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONFIGSTORE_V1_1_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace configstore {
namespace V1_1 {

// Forward declaration for forward reference support:
enum class DisplayOrientation : uint8_t;
struct OptionalDisplayOrientation;

/**
 * Orientation in degrees.
 */
enum class DisplayOrientation : uint8_t {
    ORIENTATION_0 = 0,
    ORIENTATION_90 = 1 /* ::android::hardware::configstore::V1_1::DisplayOrientation.ORIENTATION_0 implicitly + 1 */,
    ORIENTATION_180 = 2 /* ::android::hardware::configstore::V1_1::DisplayOrientation.ORIENTATION_90 implicitly + 1 */,
    ORIENTATION_270 = 3 /* ::android::hardware::configstore::V1_1::DisplayOrientation.ORIENTATION_180 implicitly + 1 */,
};

struct OptionalDisplayOrientation final {
    bool specified __attribute__ ((aligned(1)));
    ::android::hardware::configstore::V1_1::DisplayOrientation value __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::configstore::V1_1::OptionalDisplayOrientation, specified) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::configstore::V1_1::OptionalDisplayOrientation, value) == 1, "wrong offset");
static_assert(sizeof(::android::hardware::configstore::V1_1::OptionalDisplayOrientation) == 2, "wrong size");
static_assert(__alignof(::android::hardware::configstore::V1_1::OptionalDisplayOrientation) == 1, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::configstore::V1_1::DisplayOrientation o);
static inline void PrintTo(::android::hardware::configstore::V1_1::DisplayOrientation o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::configstore::V1_1::DisplayOrientation lhs, const ::android::hardware::configstore::V1_1::DisplayOrientation rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::configstore::V1_1::DisplayOrientation rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::configstore::V1_1::DisplayOrientation lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::configstore::V1_1::DisplayOrientation lhs, const ::android::hardware::configstore::V1_1::DisplayOrientation rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::configstore::V1_1::DisplayOrientation rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::configstore::V1_1::DisplayOrientation lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::configstore::V1_1::DisplayOrientation e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::configstore::V1_1::DisplayOrientation e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& o);
static inline void PrintTo(const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& lhs, const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& rhs);
static inline bool operator!=(const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& lhs, const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::configstore::V1_1::DisplayOrientation>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::configstore::V1_1::DisplayOrientation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_0) == static_cast<uint8_t>(::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_0)) {
        os += (first ? "" : " | ");
        os += "ORIENTATION_0";
        first = false;
        flipped |= ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_0;
    }
    if ((o & ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_90) == static_cast<uint8_t>(::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_90)) {
        os += (first ? "" : " | ");
        os += "ORIENTATION_90";
        first = false;
        flipped |= ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_90;
    }
    if ((o & ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_180) == static_cast<uint8_t>(::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_180)) {
        os += (first ? "" : " | ");
        os += "ORIENTATION_180";
        first = false;
        flipped |= ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_180;
    }
    if ((o & ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_270) == static_cast<uint8_t>(::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_270)) {
        os += (first ? "" : " | ");
        os += "ORIENTATION_270";
        first = false;
        flipped |= ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_270;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::configstore::V1_1::DisplayOrientation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_0) {
        return "ORIENTATION_0";
    }
    if (o == ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_90) {
        return "ORIENTATION_90";
    }
    if (o == ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_180) {
        return "ORIENTATION_180";
    }
    if (o == ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_270) {
        return "ORIENTATION_270";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::configstore::V1_1::DisplayOrientation o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".specified = ";
    os += ::android::hardware::toString(o.specified);
    os += ", .value = ";
    os += ::android::hardware::configstore::V1_1::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& lhs, const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& rhs) {
    if (lhs.specified != rhs.specified) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& lhs, const ::android::hardware::configstore::V1_1::OptionalDisplayOrientation& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_1
}  // namespace configstore
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
template<> inline constexpr std::array<::android::hardware::configstore::V1_1::DisplayOrientation, 4> hidl_enum_values<::android::hardware::configstore::V1_1::DisplayOrientation> = {
    ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_0,
    ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_90,
    ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_180,
    ::android::hardware::configstore::V1_1::DisplayOrientation::ORIENTATION_270,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONFIGSTORE_V1_1_TYPES_H
