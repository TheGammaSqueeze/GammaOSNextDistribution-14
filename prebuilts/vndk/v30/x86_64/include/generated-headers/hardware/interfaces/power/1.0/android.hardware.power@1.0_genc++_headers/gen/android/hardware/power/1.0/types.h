#ifndef HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace power {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class PowerHint : uint32_t;
enum class Feature : uint32_t;
enum class Status : uint32_t;
struct PowerStateVoter;
struct PowerStatePlatformSleepState;

/**
 * Power hint identifiers passed to powerHint()
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
};

enum class Feature : uint32_t {
    /**
     * Enabling/Disabling this feature will allow/disallow the system
     * to wake up by tapping the screen twice.
     */
    POWER_FEATURE_DOUBLE_TAP_TO_WAKE = 1u /* 0x00000001 */,
};

enum class Status : uint32_t {
    SUCCESS = 0u,
    FILESYSTEM_ERROR = 1u,
};

/**
 * Platform-level sleep state stats:
 * PowerStateVoter struct is useful for describing the individual voters
 * when a Platform-level sleep state is chosen by aggregation of votes from
 * multiple clients/system conditions.
 *
 * This helps in attirbuting what in the device is blocking the device from
 * entering the lowest Platform-level sleep state.
 */
struct PowerStateVoter final {
    /**
     * Name of the voter.
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * Total time in msec the voter voted for the platform sleep state since
     * boot.
     */
    uint64_t totalTimeInMsecVotedForSinceBoot __attribute__ ((aligned(8)));
    /**
     * Number of times the voter voted for the platform sleep state since boot.
     */
    uint64_t totalNumberOfTimesVotedSinceBoot __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::power::V1_0::PowerStateVoter, name) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_0::PowerStateVoter, totalTimeInMsecVotedForSinceBoot) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_0::PowerStateVoter, totalNumberOfTimesVotedSinceBoot) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::power::V1_0::PowerStateVoter) == 32, "wrong size");
static_assert(__alignof(::android::hardware::power::V1_0::PowerStateVoter) == 8, "wrong alignment");

/**
 * Platform-level sleep state stats:
 * PowerStatePlatformSleepState represents the Platform-level sleep state
 * the device is capable of getting into.
 *
 * SoCs usually have more than one Platform-level sleep state.
 */
struct PowerStatePlatformSleepState final {
    /**
     * Platform-level Sleep state name.
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * Time spent in msec at this platform-level sleep state since boot.
     */
    uint64_t residencyInMsecSinceBoot __attribute__ ((aligned(8)));
    /**
     * Total number of times system entered this state.
     */
    uint64_t totalTransitions __attribute__ ((aligned(8)));
    /**
     * This platform-level sleep state can only be reached during system suspend
     */
    bool supportedOnlyInSuspend __attribute__ ((aligned(1)));
    /**
     * voters is useful if the Platform-level sleep state
     * is chosen by aggregation votes from multiple clients/system conditions.
     * All the voters have to say yes or all the system conditions need to be
     * met to enter a platform-level sleep state.
     *
     * Vector of size zero implies either the info is not available
     * or the system does not follow a voting mechanism to choose this
     * Platform-level sleep state.
     */
    ::android::hardware::hidl_vec<::android::hardware::power::V1_0::PowerStateVoter> voters __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::power::V1_0::PowerStatePlatformSleepState, name) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_0::PowerStatePlatformSleepState, residencyInMsecSinceBoot) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_0::PowerStatePlatformSleepState, totalTransitions) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_0::PowerStatePlatformSleepState, supportedOnlyInSuspend) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_0::PowerStatePlatformSleepState, voters) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::power::V1_0::PowerStatePlatformSleepState) == 56, "wrong size");
static_assert(__alignof(::android::hardware::power::V1_0::PowerStatePlatformSleepState) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::power::V1_0::PowerHint o);
static inline void PrintTo(::android::hardware::power::V1_0::PowerHint o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::power::V1_0::PowerHint lhs, const ::android::hardware::power::V1_0::PowerHint rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::power::V1_0::PowerHint rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::power::V1_0::PowerHint lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::power::V1_0::PowerHint lhs, const ::android::hardware::power::V1_0::PowerHint rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::power::V1_0::PowerHint rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::power::V1_0::PowerHint lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::power::V1_0::PowerHint e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::power::V1_0::PowerHint e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::power::V1_0::Feature o);
static inline void PrintTo(::android::hardware::power::V1_0::Feature o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::power::V1_0::Feature lhs, const ::android::hardware::power::V1_0::Feature rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::power::V1_0::Feature rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::power::V1_0::Feature lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::power::V1_0::Feature lhs, const ::android::hardware::power::V1_0::Feature rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::power::V1_0::Feature rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::power::V1_0::Feature lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::power::V1_0::Feature e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::power::V1_0::Feature e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::power::V1_0::Status o);
static inline void PrintTo(::android::hardware::power::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::power::V1_0::Status lhs, const ::android::hardware::power::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::power::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::power::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::power::V1_0::Status lhs, const ::android::hardware::power::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::power::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::power::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::power::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::power::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::power::V1_0::PowerStateVoter& o);
static inline void PrintTo(const ::android::hardware::power::V1_0::PowerStateVoter& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::V1_0::PowerStateVoter& lhs, const ::android::hardware::power::V1_0::PowerStateVoter& rhs);
static inline bool operator!=(const ::android::hardware::power::V1_0::PowerStateVoter& lhs, const ::android::hardware::power::V1_0::PowerStateVoter& rhs);

static inline std::string toString(const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& o);
static inline void PrintTo(const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& lhs, const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& rhs);
static inline bool operator!=(const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& lhs, const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::power::V1_0::PowerHint>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::power::V1_0::PowerHint> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::power::V1_0::PowerHint::VSYNC) == static_cast<uint32_t>(::android::hardware::power::V1_0::PowerHint::VSYNC)) {
        os += (first ? "" : " | ");
        os += "VSYNC";
        first = false;
        flipped |= ::android::hardware::power::V1_0::PowerHint::VSYNC;
    }
    if ((o & ::android::hardware::power::V1_0::PowerHint::INTERACTION) == static_cast<uint32_t>(::android::hardware::power::V1_0::PowerHint::INTERACTION)) {
        os += (first ? "" : " | ");
        os += "INTERACTION";
        first = false;
        flipped |= ::android::hardware::power::V1_0::PowerHint::INTERACTION;
    }
    if ((o & ::android::hardware::power::V1_0::PowerHint::VIDEO_ENCODE) == static_cast<uint32_t>(::android::hardware::power::V1_0::PowerHint::VIDEO_ENCODE)) {
        os += (first ? "" : " | ");
        os += "VIDEO_ENCODE";
        first = false;
        flipped |= ::android::hardware::power::V1_0::PowerHint::VIDEO_ENCODE;
    }
    if ((o & ::android::hardware::power::V1_0::PowerHint::VIDEO_DECODE) == static_cast<uint32_t>(::android::hardware::power::V1_0::PowerHint::VIDEO_DECODE)) {
        os += (first ? "" : " | ");
        os += "VIDEO_DECODE";
        first = false;
        flipped |= ::android::hardware::power::V1_0::PowerHint::VIDEO_DECODE;
    }
    if ((o & ::android::hardware::power::V1_0::PowerHint::LOW_POWER) == static_cast<uint32_t>(::android::hardware::power::V1_0::PowerHint::LOW_POWER)) {
        os += (first ? "" : " | ");
        os += "LOW_POWER";
        first = false;
        flipped |= ::android::hardware::power::V1_0::PowerHint::LOW_POWER;
    }
    if ((o & ::android::hardware::power::V1_0::PowerHint::SUSTAINED_PERFORMANCE) == static_cast<uint32_t>(::android::hardware::power::V1_0::PowerHint::SUSTAINED_PERFORMANCE)) {
        os += (first ? "" : " | ");
        os += "SUSTAINED_PERFORMANCE";
        first = false;
        flipped |= ::android::hardware::power::V1_0::PowerHint::SUSTAINED_PERFORMANCE;
    }
    if ((o & ::android::hardware::power::V1_0::PowerHint::VR_MODE) == static_cast<uint32_t>(::android::hardware::power::V1_0::PowerHint::VR_MODE)) {
        os += (first ? "" : " | ");
        os += "VR_MODE";
        first = false;
        flipped |= ::android::hardware::power::V1_0::PowerHint::VR_MODE;
    }
    if ((o & ::android::hardware::power::V1_0::PowerHint::LAUNCH) == static_cast<uint32_t>(::android::hardware::power::V1_0::PowerHint::LAUNCH)) {
        os += (first ? "" : " | ");
        os += "LAUNCH";
        first = false;
        flipped |= ::android::hardware::power::V1_0::PowerHint::LAUNCH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::power::V1_0::PowerHint o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::power::V1_0::PowerHint::VSYNC) {
        return "VSYNC";
    }
    if (o == ::android::hardware::power::V1_0::PowerHint::INTERACTION) {
        return "INTERACTION";
    }
    if (o == ::android::hardware::power::V1_0::PowerHint::VIDEO_ENCODE) {
        return "VIDEO_ENCODE";
    }
    if (o == ::android::hardware::power::V1_0::PowerHint::VIDEO_DECODE) {
        return "VIDEO_DECODE";
    }
    if (o == ::android::hardware::power::V1_0::PowerHint::LOW_POWER) {
        return "LOW_POWER";
    }
    if (o == ::android::hardware::power::V1_0::PowerHint::SUSTAINED_PERFORMANCE) {
        return "SUSTAINED_PERFORMANCE";
    }
    if (o == ::android::hardware::power::V1_0::PowerHint::VR_MODE) {
        return "VR_MODE";
    }
    if (o == ::android::hardware::power::V1_0::PowerHint::LAUNCH) {
        return "LAUNCH";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::power::V1_0::PowerHint o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::power::V1_0::Feature>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::power::V1_0::Feature> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::power::V1_0::Feature::POWER_FEATURE_DOUBLE_TAP_TO_WAKE) == static_cast<uint32_t>(::android::hardware::power::V1_0::Feature::POWER_FEATURE_DOUBLE_TAP_TO_WAKE)) {
        os += (first ? "" : " | ");
        os += "POWER_FEATURE_DOUBLE_TAP_TO_WAKE";
        first = false;
        flipped |= ::android::hardware::power::V1_0::Feature::POWER_FEATURE_DOUBLE_TAP_TO_WAKE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::power::V1_0::Feature o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::power::V1_0::Feature::POWER_FEATURE_DOUBLE_TAP_TO_WAKE) {
        return "POWER_FEATURE_DOUBLE_TAP_TO_WAKE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::power::V1_0::Feature o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::power::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::power::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::power::V1_0::Status::SUCCESS) == static_cast<uint32_t>(::android::hardware::power::V1_0::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::power::V1_0::Status::SUCCESS;
    }
    if ((o & ::android::hardware::power::V1_0::Status::FILESYSTEM_ERROR) == static_cast<uint32_t>(::android::hardware::power::V1_0::Status::FILESYSTEM_ERROR)) {
        os += (first ? "" : " | ");
        os += "FILESYSTEM_ERROR";
        first = false;
        flipped |= ::android::hardware::power::V1_0::Status::FILESYSTEM_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::power::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::power::V1_0::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::power::V1_0::Status::FILESYSTEM_ERROR) {
        return "FILESYSTEM_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::power::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::power::V1_0::PowerStateVoter& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .totalTimeInMsecVotedForSinceBoot = ";
    os += ::android::hardware::toString(o.totalTimeInMsecVotedForSinceBoot);
    os += ", .totalNumberOfTimesVotedSinceBoot = ";
    os += ::android::hardware::toString(o.totalNumberOfTimesVotedSinceBoot);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::V1_0::PowerStateVoter& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::V1_0::PowerStateVoter& lhs, const ::android::hardware::power::V1_0::PowerStateVoter& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.totalTimeInMsecVotedForSinceBoot != rhs.totalTimeInMsecVotedForSinceBoot) {
        return false;
    }
    if (lhs.totalNumberOfTimesVotedSinceBoot != rhs.totalNumberOfTimesVotedSinceBoot) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::V1_0::PowerStateVoter& lhs, const ::android::hardware::power::V1_0::PowerStateVoter& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .residencyInMsecSinceBoot = ";
    os += ::android::hardware::toString(o.residencyInMsecSinceBoot);
    os += ", .totalTransitions = ";
    os += ::android::hardware::toString(o.totalTransitions);
    os += ", .supportedOnlyInSuspend = ";
    os += ::android::hardware::toString(o.supportedOnlyInSuspend);
    os += ", .voters = ";
    os += ::android::hardware::toString(o.voters);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& lhs, const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.residencyInMsecSinceBoot != rhs.residencyInMsecSinceBoot) {
        return false;
    }
    if (lhs.totalTransitions != rhs.totalTransitions) {
        return false;
    }
    if (lhs.supportedOnlyInSuspend != rhs.supportedOnlyInSuspend) {
        return false;
    }
    if (lhs.voters != rhs.voters) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& lhs, const ::android::hardware::power::V1_0::PowerStatePlatformSleepState& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
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
template<> inline constexpr std::array<::android::hardware::power::V1_0::PowerHint, 8> hidl_enum_values<::android::hardware::power::V1_0::PowerHint> = {
    ::android::hardware::power::V1_0::PowerHint::VSYNC,
    ::android::hardware::power::V1_0::PowerHint::INTERACTION,
    ::android::hardware::power::V1_0::PowerHint::VIDEO_ENCODE,
    ::android::hardware::power::V1_0::PowerHint::VIDEO_DECODE,
    ::android::hardware::power::V1_0::PowerHint::LOW_POWER,
    ::android::hardware::power::V1_0::PowerHint::SUSTAINED_PERFORMANCE,
    ::android::hardware::power::V1_0::PowerHint::VR_MODE,
    ::android::hardware::power::V1_0::PowerHint::LAUNCH,
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
template<> inline constexpr std::array<::android::hardware::power::V1_0::Feature, 1> hidl_enum_values<::android::hardware::power::V1_0::Feature> = {
    ::android::hardware::power::V1_0::Feature::POWER_FEATURE_DOUBLE_TAP_TO_WAKE,
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
template<> inline constexpr std::array<::android::hardware::power::V1_0::Status, 2> hidl_enum_values<::android::hardware::power::V1_0::Status> = {
    ::android::hardware::power::V1_0::Status::SUCCESS,
    ::android::hardware::power::V1_0::Status::FILESYSTEM_ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_0_TYPES_H
