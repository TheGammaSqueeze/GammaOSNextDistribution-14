#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_TYPES_H

#include <android/hardware/biometrics/fingerprint/2.1/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace fingerprint {
namespace V2_2 {

// Forward declaration for forward reference support:
enum class FingerprintAcquiredInfo : int32_t;

/**
 * Fingerprint acquisition info is meant as feedback for the current operation.
 * Anything but START and ACQUIRED_GOOD must be shown to the user as feedback on
 * how to take action on the current operation. For example,
 * ACQUIRED_IMAGER_DIRTY may be used to tell the user to clean the sensor if it
 * is detected to be dirty.
 * If this causes the current operation to fail, an additional ERROR_CANCELED
 * must be sent to stop the operation in progress (e.g. enrollment).
 * In general, these messages will result in a "Try again" message.
 */
enum class FingerprintAcquiredInfo : int32_t {
    ACQUIRED_GOOD = 0,
    /**
     * sensor needs more data, i.e. longer swipe.
     */
    ACQUIRED_PARTIAL = 1,
    /**
     * image doesn't contain enough detail for recognition
     */
    ACQUIRED_INSUFFICIENT = 2,
    /**
     * sensor needs to be cleaned
     */
    ACQUIRED_IMAGER_DIRTY = 3,
    /**
     * mostly swipe-type sensors; not enough data collected
     */
    ACQUIRED_TOO_SLOW = 4,
    /**
     * vendor-specific acquisition messages start here
     */
    ACQUIRED_TOO_FAST = 5,
    /**
     * vendor-specific acquisition messages
     */
    ACQUIRED_VENDOR = 6,
    /**
     * This message represents the earliest message sent at the beginning of the
     * authentication pipeline. It is expected to be used to measure latency. For
     * example, in a camera-based authentication system it's expected to be sent
     * prior to camera initialization. Note this should be sent whenever
     * authentication is restarted (see IBiometricsFace#userActivity).
     * The framework will measure latency based on the time between the last START
     * message and the onAuthenticated callback.
     */
    START = 7,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo o);
static inline void PrintTo(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo lhs, const ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo lhs, const ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo e) {
    v &= static_cast<int32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_GOOD) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_GOOD)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_GOOD";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_GOOD;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_PARTIAL) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_PARTIAL)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_PARTIAL";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_PARTIAL;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_INSUFFICIENT) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_INSUFFICIENT)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_INSUFFICIENT";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_INSUFFICIENT;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_IMAGER_DIRTY) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_IMAGER_DIRTY)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_IMAGER_DIRTY";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_IMAGER_DIRTY;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_TOO_SLOW) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_TOO_SLOW)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_TOO_SLOW";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_TOO_SLOW;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_TOO_FAST) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_TOO_FAST)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_TOO_FAST";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_TOO_FAST;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_VENDOR) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_VENDOR)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_VENDOR";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_VENDOR;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::START) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::START)) {
        os += (first ? "" : " | ");
        os += "START";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::START;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_GOOD) {
        return "ACQUIRED_GOOD";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_PARTIAL) {
        return "ACQUIRED_PARTIAL";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_INSUFFICIENT) {
        return "ACQUIRED_INSUFFICIENT";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_IMAGER_DIRTY) {
        return "ACQUIRED_IMAGER_DIRTY";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_TOO_SLOW) {
        return "ACQUIRED_TOO_SLOW";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_TOO_FAST) {
        return "ACQUIRED_TOO_FAST";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_VENDOR) {
        return "ACQUIRED_VENDOR";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::START) {
        return "START";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V2_2
}  // namespace fingerprint
}  // namespace biometrics
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
template<> inline constexpr std::array<::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo, 8> hidl_enum_values<::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo> = {
    ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_GOOD,
    ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_PARTIAL,
    ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_INSUFFICIENT,
    ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_IMAGER_DIRTY,
    ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_TOO_SLOW,
    ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_TOO_FAST,
    ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::ACQUIRED_VENDOR,
    ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo::START,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_TYPES_H
