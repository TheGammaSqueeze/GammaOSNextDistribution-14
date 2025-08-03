#ifndef HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_1_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace power {
namespace V1_1 {

// Forward declaration for forward reference support:
struct PowerStateSubsystemSleepState;
struct PowerStateSubsystem;

/**
 * Subsytem-level sleep state stats:
 * PowerStateSubsystemSleepState represents the sleep states
 * a subsystem (e.g. wifi, bt) is capable of getting into.
 *
 * SoCs like wifi, bt usually have more than one subsystem level sleep state.
 */
struct PowerStateSubsystemSleepState final {
    /**
     * Subsystem-level Sleep state name.
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * Time spent in msec at this subsystem-level sleep state since boot.
     */
    uint64_t residencyInMsecSinceBoot __attribute__ ((aligned(8)));
    /**
     * Total number of times sub-system entered this state.
     */
    uint64_t totalTransitions __attribute__ ((aligned(8)));
    /**
     * Timestamp of last entry of this state measured in MSec
     */
    uint64_t lastEntryTimestampMs __attribute__ ((aligned(8)));
    /**
     * This subsystem-level sleep state can only be reached during system suspend
     */
    bool supportedOnlyInSuspend __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::power::V1_1::PowerStateSubsystemSleepState, name) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_1::PowerStateSubsystemSleepState, residencyInMsecSinceBoot) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_1::PowerStateSubsystemSleepState, totalTransitions) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_1::PowerStateSubsystemSleepState, lastEntryTimestampMs) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_1::PowerStateSubsystemSleepState, supportedOnlyInSuspend) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::power::V1_1::PowerStateSubsystemSleepState) == 48, "wrong size");
static_assert(__alignof(::android::hardware::power::V1_1::PowerStateSubsystemSleepState) == 8, "wrong alignment");

/**
 * Subsytem-level sleep state stats:
 * PowerStateSubsystem represents a subsystem (e.g. wifi, bt)
 * and all the sleep states this susbsystem is capable of getting into.
 *
 * SoCs like wifi, bt usually have more than one subsystem level sleep state.
 */
struct PowerStateSubsystem final {
    /**
     * Subsystem name (e.g. wifi, bt etc.)
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * states represents the list of sleep states supported by this susbsystem.
     * Higher the index in the returned <states> vector deeper the state is
     * i.e. lesser steady-state power is consumed by the subsystem to
     * to be resident in that state.
     *
     * Vector of size zero implies either the info is not available
     * or the subsystem does not have any sleep states.
     */
    ::android::hardware::hidl_vec<::android::hardware::power::V1_1::PowerStateSubsystemSleepState> states __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::power::V1_1::PowerStateSubsystem, name) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::V1_1::PowerStateSubsystem, states) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::power::V1_1::PowerStateSubsystem) == 32, "wrong size");
static_assert(__alignof(::android::hardware::power::V1_1::PowerStateSubsystem) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& o);
static inline void PrintTo(const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& lhs, const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& rhs);
static inline bool operator!=(const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& lhs, const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& rhs);

static inline std::string toString(const ::android::hardware::power::V1_1::PowerStateSubsystem& o);
static inline void PrintTo(const ::android::hardware::power::V1_1::PowerStateSubsystem& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::V1_1::PowerStateSubsystem& lhs, const ::android::hardware::power::V1_1::PowerStateSubsystem& rhs);
static inline bool operator!=(const ::android::hardware::power::V1_1::PowerStateSubsystem& lhs, const ::android::hardware::power::V1_1::PowerStateSubsystem& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .residencyInMsecSinceBoot = ";
    os += ::android::hardware::toString(o.residencyInMsecSinceBoot);
    os += ", .totalTransitions = ";
    os += ::android::hardware::toString(o.totalTransitions);
    os += ", .lastEntryTimestampMs = ";
    os += ::android::hardware::toString(o.lastEntryTimestampMs);
    os += ", .supportedOnlyInSuspend = ";
    os += ::android::hardware::toString(o.supportedOnlyInSuspend);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& lhs, const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.residencyInMsecSinceBoot != rhs.residencyInMsecSinceBoot) {
        return false;
    }
    if (lhs.totalTransitions != rhs.totalTransitions) {
        return false;
    }
    if (lhs.lastEntryTimestampMs != rhs.lastEntryTimestampMs) {
        return false;
    }
    if (lhs.supportedOnlyInSuspend != rhs.supportedOnlyInSuspend) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& lhs, const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::power::V1_1::PowerStateSubsystem& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .states = ";
    os += ::android::hardware::toString(o.states);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::V1_1::PowerStateSubsystem& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::V1_1::PowerStateSubsystem& lhs, const ::android::hardware::power::V1_1::PowerStateSubsystem& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.states != rhs.states) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::V1_1::PowerStateSubsystem& lhs, const ::android::hardware::power::V1_1::PowerStateSubsystem& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_1
}  // namespace power
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_1_TYPES_H
