#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DUMPSTATE_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DUMPSTATE_V1_1_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace dumpstate {
namespace V1_1 {

// Forward declaration for forward reference support:
enum class DumpstateMode : uint32_t;
enum class DumpstateStatus : uint32_t;

/**
 * Constants that define the type of bug report being taken to restrict content appropriately.
 */
enum class DumpstateMode : uint32_t {
    /**
     * Takes a bug report without user interference.
     */
    FULL = 0u,
    /**
     * Interactive bug report, i.e. triggered by the user.
     */
    INTERACTIVE = 1u,
    /**
     * Remote bug report triggered by DevicePolicyManager, for example.
     */
    REMOTE = 2u,
    /**
     * Bug report triggered on a wear device.
     */
    WEAR = 3u,
    /**
     * Bug report limited to only connectivity info (cellular, wifi, and networking). Sometimes
     * called "telephony" in legacy contexts.
     *
     * All reported information MUST directly relate to connectivity debugging or customer support
     * and MUST NOT contain unrelated private information. This information MUST NOT identify
     * user-installed packages (UIDs are OK, package names are not), and MUST NOT contain logs of
     * user application traffic.
     */
    CONNECTIVITY = 4u,
    /**
     * Bug report limited to only wifi info.
     */
    WIFI = 5u,
    /**
     * Default mode, essentially analogous to calling @1.0::IDumpstateDevice.dumpstateBoard(handle).
     * This mode MUST be supported if the dumpstate HAL is implemented.
     */
    DEFAULT = 6u,
    /**
     * Takes a report in protobuf.
     *
     * The content, if implemented, must be a binary protobuf message written to the first file
     * descriptor of the native handle. The protobuf schema shall be defined by the vendor.
     */
    PROTO = 7u,
};

/**
 * A simple return enum for use with dumpstateBoard_1_1.
 */
enum class DumpstateStatus : uint32_t {
    OK = 0u,
    /**
     * Returned for cases where the device doesn't support the given DumpstateMode (e.g. a phone
     * trying to use DumpstateMode::WEAR).
     */
    UNSUPPORTED_MODE = 1u,
    /**
     * Returned for cases where an IllegalArgumentException is typically appropriate, e.g. missing
     * file descriptors.
     */
    ILLEGAL_ARGUMENT = 2u,
    /**
     * Returned when device logging is not enabled.
     */
    DEVICE_LOGGING_NOT_ENABLED = 3u,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::dumpstate::V1_1::DumpstateMode o);
static inline void PrintTo(::android::hardware::dumpstate::V1_1::DumpstateMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::dumpstate::V1_1::DumpstateMode lhs, const ::android::hardware::dumpstate::V1_1::DumpstateMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::dumpstate::V1_1::DumpstateMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::dumpstate::V1_1::DumpstateMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::dumpstate::V1_1::DumpstateMode lhs, const ::android::hardware::dumpstate::V1_1::DumpstateMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::dumpstate::V1_1::DumpstateMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::dumpstate::V1_1::DumpstateMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::dumpstate::V1_1::DumpstateMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::dumpstate::V1_1::DumpstateMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::dumpstate::V1_1::DumpstateStatus o);
static inline void PrintTo(::android::hardware::dumpstate::V1_1::DumpstateStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::dumpstate::V1_1::DumpstateStatus lhs, const ::android::hardware::dumpstate::V1_1::DumpstateStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::dumpstate::V1_1::DumpstateStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::dumpstate::V1_1::DumpstateStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::dumpstate::V1_1::DumpstateStatus lhs, const ::android::hardware::dumpstate::V1_1::DumpstateStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::dumpstate::V1_1::DumpstateStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::dumpstate::V1_1::DumpstateStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::dumpstate::V1_1::DumpstateStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::dumpstate::V1_1::DumpstateStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::dumpstate::V1_1::DumpstateMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::dumpstate::V1_1::DumpstateMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateMode::FULL) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateMode::FULL)) {
        os += (first ? "" : " | ");
        os += "FULL";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateMode::FULL;
    }
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateMode::INTERACTIVE) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateMode::INTERACTIVE)) {
        os += (first ? "" : " | ");
        os += "INTERACTIVE";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateMode::INTERACTIVE;
    }
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateMode::REMOTE) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateMode::REMOTE)) {
        os += (first ? "" : " | ");
        os += "REMOTE";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateMode::REMOTE;
    }
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateMode::WEAR) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateMode::WEAR)) {
        os += (first ? "" : " | ");
        os += "WEAR";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateMode::WEAR;
    }
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateMode::CONNECTIVITY) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateMode::CONNECTIVITY)) {
        os += (first ? "" : " | ");
        os += "CONNECTIVITY";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateMode::CONNECTIVITY;
    }
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateMode::WIFI) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateMode::WIFI)) {
        os += (first ? "" : " | ");
        os += "WIFI";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateMode::WIFI;
    }
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateMode::DEFAULT) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateMode::DEFAULT)) {
        os += (first ? "" : " | ");
        os += "DEFAULT";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateMode::DEFAULT;
    }
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateMode::PROTO) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateMode::PROTO)) {
        os += (first ? "" : " | ");
        os += "PROTO";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateMode::PROTO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::dumpstate::V1_1::DumpstateMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateMode::FULL) {
        return "FULL";
    }
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateMode::INTERACTIVE) {
        return "INTERACTIVE";
    }
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateMode::REMOTE) {
        return "REMOTE";
    }
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateMode::WEAR) {
        return "WEAR";
    }
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateMode::CONNECTIVITY) {
        return "CONNECTIVITY";
    }
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateMode::WIFI) {
        return "WIFI";
    }
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateMode::DEFAULT) {
        return "DEFAULT";
    }
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateMode::PROTO) {
        return "PROTO";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::dumpstate::V1_1::DumpstateMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::dumpstate::V1_1::DumpstateStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::dumpstate::V1_1::DumpstateStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateStatus::OK) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateStatus::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateStatus::OK;
    }
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateStatus::UNSUPPORTED_MODE) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateStatus::UNSUPPORTED_MODE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_MODE";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateStatus::UNSUPPORTED_MODE;
    }
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateStatus::ILLEGAL_ARGUMENT) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateStatus::ILLEGAL_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "ILLEGAL_ARGUMENT";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateStatus::ILLEGAL_ARGUMENT;
    }
    if ((o & ::android::hardware::dumpstate::V1_1::DumpstateStatus::DEVICE_LOGGING_NOT_ENABLED) == static_cast<uint32_t>(::android::hardware::dumpstate::V1_1::DumpstateStatus::DEVICE_LOGGING_NOT_ENABLED)) {
        os += (first ? "" : " | ");
        os += "DEVICE_LOGGING_NOT_ENABLED";
        first = false;
        flipped |= ::android::hardware::dumpstate::V1_1::DumpstateStatus::DEVICE_LOGGING_NOT_ENABLED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::dumpstate::V1_1::DumpstateStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateStatus::OK) {
        return "OK";
    }
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateStatus::UNSUPPORTED_MODE) {
        return "UNSUPPORTED_MODE";
    }
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateStatus::ILLEGAL_ARGUMENT) {
        return "ILLEGAL_ARGUMENT";
    }
    if (o == ::android::hardware::dumpstate::V1_1::DumpstateStatus::DEVICE_LOGGING_NOT_ENABLED) {
        return "DEVICE_LOGGING_NOT_ENABLED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::dumpstate::V1_1::DumpstateStatus o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_1
}  // namespace dumpstate
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
template<> inline constexpr std::array<::android::hardware::dumpstate::V1_1::DumpstateMode, 8> hidl_enum_values<::android::hardware::dumpstate::V1_1::DumpstateMode> = {
    ::android::hardware::dumpstate::V1_1::DumpstateMode::FULL,
    ::android::hardware::dumpstate::V1_1::DumpstateMode::INTERACTIVE,
    ::android::hardware::dumpstate::V1_1::DumpstateMode::REMOTE,
    ::android::hardware::dumpstate::V1_1::DumpstateMode::WEAR,
    ::android::hardware::dumpstate::V1_1::DumpstateMode::CONNECTIVITY,
    ::android::hardware::dumpstate::V1_1::DumpstateMode::WIFI,
    ::android::hardware::dumpstate::V1_1::DumpstateMode::DEFAULT,
    ::android::hardware::dumpstate::V1_1::DumpstateMode::PROTO,
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
template<> inline constexpr std::array<::android::hardware::dumpstate::V1_1::DumpstateStatus, 4> hidl_enum_values<::android::hardware::dumpstate::V1_1::DumpstateStatus> = {
    ::android::hardware::dumpstate::V1_1::DumpstateStatus::OK,
    ::android::hardware::dumpstate::V1_1::DumpstateStatus::UNSUPPORTED_MODE,
    ::android::hardware::dumpstate::V1_1::DumpstateStatus::ILLEGAL_ARGUMENT,
    ::android::hardware::dumpstate::V1_1::DumpstateStatus::DEVICE_LOGGING_NOT_ENABLED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DUMPSTATE_V1_1_TYPES_H
