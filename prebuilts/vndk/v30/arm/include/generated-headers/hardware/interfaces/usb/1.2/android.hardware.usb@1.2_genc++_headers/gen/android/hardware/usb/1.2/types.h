#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_2_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_2_TYPES_H

#include <android/hardware/usb/1.0/types.h>
#include <android/hardware/usb/1.1/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace usb {
namespace V1_2 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;
enum class ContaminantDetectionStatus : uint32_t;
enum class ContaminantProtectionMode : uint32_t;
enum class ContaminantProtectionStatus : uint32_t;
struct PortStatus;

enum class Status : uint32_t {
    SUCCESS = 0u,
    /**
     * error value when the HAL operation fails for reasons not listed here.
     */
    ERROR = 1u,
    /**
     * error value returned when input argument is invalid.
     */
    INVALID_ARGUMENT = 2u,
    /**
     * error value returned when role string is unrecognized.
     */
    UNRECOGNIZED_ROLE = 3u,
    /**
     * Error value returned when the operation is not supported.
     */
    NOT_SUPPORTED = 4u,
};

enum class ContaminantDetectionStatus : uint32_t {
    /**
     * Contaminant presence detection is not supported.
     */
    NOT_SUPPORTED = 0u,
    /**
     * Contaminant presence detection is supported but disabled.
     */
    DISABLED = 1u,
    /**
     * Contaminant presence detection is enabled and contaminant not detected.
     */
    NOT_DETECTED = 2u,
    /**
     * Contaminant presence detection is enabled and contaminant detected.
     */
    DETECTED = 3u,
};

enum class ContaminantProtectionMode : uint32_t {
    /**
     * No action performed upon detection of contaminant presence.
     */
    NONE = 0u,
    /**
     * Upon detection of contaminant presence, Port is forced to sink only
     * mode where a port shall only detect chargers until contaminant presence
     * is no longer detected.
     */
    FORCE_SINK = 1u /* 1 << 0 */,
    /**
     * Upon detection of contaminant presence, Port is forced to source only
     * mode where a port shall only detect usb accessories such as headsets
     * until contaminant presence is no longer detected.
     */
    FORCE_SOURCE = 2u /* 1 << 1 */,
    /**
     * Upon detection of contaminant presence, port is disabled until contaminant
     * presence is no longer detected. In the disabled state port will
     * not respond to connection of chargers or usb accessories.
     */
    FORCE_DISABLE = 4u /* 1 << 2 */,
};

enum class ContaminantProtectionStatus : uint32_t {
    /**
     * No action performed upon detection of contaminant presence.
     */
    NONE = 0u,
    /**
     * Upon detection of contaminant presence, Port is forced to sink only
     * mode where a port shall only detect chargers until contaminant presence
     * is no longer detected.
     */
    FORCE_SINK = 1u /* 1 << 0 */,
    /**
     * Upon detection of contaminant presence, Port is forced to source only
     * mode where a port shall only detect usb accessories such as headsets
     * until contaminant presence is no longer detected.
     */
    FORCE_SOURCE = 2u /* 1 << 1 */,
    /**
     * Upon detection of contaminant presence, port is disabled until contaminant
     * presence is no longer detected. In the disabled state port will
     * not respond to connection of chargers or usb accessories.
     */
    FORCE_DISABLE = 4u /* 1 << 2 */,
    /**
     * Client disabled cotaminant protection by calling
     * enableContaminantPresencePortProtection set to false. Low level drivers should
     * not autmomously take any corrective action when contaminant presence is detected.
     */
    DISABLED = 8u /* 1 << 3 */,
};

struct PortStatus final {
    ::android::hardware::usb::V1_1::PortStatus_1_1 status_1_1 __attribute__ ((aligned(8)));
    /**
     * Contaminant presence protection modes supported by the port.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_2::ContaminantProtectionMode> supportedContaminantProtectionModes __attribute__ ((aligned(4)));
    /**
     * Client can enable/disable contaminant presence protection through
     * enableContaminantPresenceProtection when true.
     */
    bool supportsEnableContaminantPresenceProtection __attribute__ ((aligned(1)));
    /**
     * Contaminant presence protection modes currently active for the port.
     */
    ::android::hardware::usb::V1_2::ContaminantProtectionStatus contaminantProtectionStatus __attribute__ ((aligned(4)));
    /**
     * Client can enable/disable contaminant presence detection through
     * enableContaminantPresenceDetection when true.
     */
    bool supportsEnableContaminantPresenceDetection __attribute__ ((aligned(1)));
    /**
     * Current status of contaminant detection algorithm.
     */
    ::android::hardware::usb::V1_2::ContaminantDetectionStatus contaminantDetectionStatus __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::usb::V1_2::PortStatus, status_1_1) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_2::PortStatus, supportedContaminantProtectionModes) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_2::PortStatus, supportsEnableContaminantPresenceProtection) == 52, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_2::PortStatus, contaminantProtectionStatus) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_2::PortStatus, supportsEnableContaminantPresenceDetection) == 60, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_2::PortStatus, contaminantDetectionStatus) == 64, "wrong offset");
static_assert(sizeof(::android::hardware::usb::V1_2::PortStatus) == 72, "wrong size");
static_assert(__alignof(::android::hardware::usb::V1_2::PortStatus) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::V1_2::Status o);
static inline void PrintTo(::android::hardware::usb::V1_2::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::V1_2::Status lhs, const ::android::hardware::usb::V1_2::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::V1_2::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::V1_2::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_2::Status lhs, const ::android::hardware::usb::V1_2::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::V1_2::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_2::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::V1_2::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::V1_2::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::V1_2::ContaminantDetectionStatus o);
static inline void PrintTo(::android::hardware::usb::V1_2::ContaminantDetectionStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::V1_2::ContaminantDetectionStatus lhs, const ::android::hardware::usb::V1_2::ContaminantDetectionStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::V1_2::ContaminantDetectionStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::V1_2::ContaminantDetectionStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_2::ContaminantDetectionStatus lhs, const ::android::hardware::usb::V1_2::ContaminantDetectionStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::V1_2::ContaminantDetectionStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_2::ContaminantDetectionStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::V1_2::ContaminantDetectionStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::V1_2::ContaminantDetectionStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::V1_2::ContaminantProtectionMode o);
static inline void PrintTo(::android::hardware::usb::V1_2::ContaminantProtectionMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::V1_2::ContaminantProtectionMode lhs, const ::android::hardware::usb::V1_2::ContaminantProtectionMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::V1_2::ContaminantProtectionMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::V1_2::ContaminantProtectionMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_2::ContaminantProtectionMode lhs, const ::android::hardware::usb::V1_2::ContaminantProtectionMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::V1_2::ContaminantProtectionMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_2::ContaminantProtectionMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::V1_2::ContaminantProtectionMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::V1_2::ContaminantProtectionMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::V1_2::ContaminantProtectionStatus o);
static inline void PrintTo(::android::hardware::usb::V1_2::ContaminantProtectionStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::V1_2::ContaminantProtectionStatus lhs, const ::android::hardware::usb::V1_2::ContaminantProtectionStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::V1_2::ContaminantProtectionStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::V1_2::ContaminantProtectionStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_2::ContaminantProtectionStatus lhs, const ::android::hardware::usb::V1_2::ContaminantProtectionStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::V1_2::ContaminantProtectionStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_2::ContaminantProtectionStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::V1_2::ContaminantProtectionStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::V1_2::ContaminantProtectionStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::usb::V1_2::PortStatus& o);
static inline void PrintTo(const ::android::hardware::usb::V1_2::PortStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::usb::V1_2::PortStatus& lhs, const ::android::hardware::usb::V1_2::PortStatus& rhs);
static inline bool operator!=(const ::android::hardware::usb::V1_2::PortStatus& lhs, const ::android::hardware::usb::V1_2::PortStatus& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::usb::V1_2::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_2::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::V1_2::Status::SUCCESS) == static_cast<uint32_t>(::android::hardware::usb::V1_2::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::Status::SUCCESS;
    }
    if ((o & ::android::hardware::usb::V1_2::Status::ERROR) == static_cast<uint32_t>(::android::hardware::usb::V1_2::Status::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::Status::ERROR;
    }
    if ((o & ::android::hardware::usb::V1_2::Status::INVALID_ARGUMENT) == static_cast<uint32_t>(::android::hardware::usb::V1_2::Status::INVALID_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENT";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::Status::INVALID_ARGUMENT;
    }
    if ((o & ::android::hardware::usb::V1_2::Status::UNRECOGNIZED_ROLE) == static_cast<uint32_t>(::android::hardware::usb::V1_2::Status::UNRECOGNIZED_ROLE)) {
        os += (first ? "" : " | ");
        os += "UNRECOGNIZED_ROLE";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::Status::UNRECOGNIZED_ROLE;
    }
    if ((o & ::android::hardware::usb::V1_2::Status::NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::usb::V1_2::Status::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::Status::NOT_SUPPORTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::V1_2::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::V1_2::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::usb::V1_2::Status::ERROR) {
        return "ERROR";
    }
    if (o == ::android::hardware::usb::V1_2::Status::INVALID_ARGUMENT) {
        return "INVALID_ARGUMENT";
    }
    if (o == ::android::hardware::usb::V1_2::Status::UNRECOGNIZED_ROLE) {
        return "UNRECOGNIZED_ROLE";
    }
    if (o == ::android::hardware::usb::V1_2::Status::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::V1_2::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::usb::V1_2::ContaminantDetectionStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_2::ContaminantDetectionStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::V1_2::ContaminantDetectionStatus::NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantDetectionStatus::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantDetectionStatus::NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::usb::V1_2::ContaminantDetectionStatus::DISABLED) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantDetectionStatus::DISABLED)) {
        os += (first ? "" : " | ");
        os += "DISABLED";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantDetectionStatus::DISABLED;
    }
    if ((o & ::android::hardware::usb::V1_2::ContaminantDetectionStatus::NOT_DETECTED) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantDetectionStatus::NOT_DETECTED)) {
        os += (first ? "" : " | ");
        os += "NOT_DETECTED";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantDetectionStatus::NOT_DETECTED;
    }
    if ((o & ::android::hardware::usb::V1_2::ContaminantDetectionStatus::DETECTED) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantDetectionStatus::DETECTED)) {
        os += (first ? "" : " | ");
        os += "DETECTED";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantDetectionStatus::DETECTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::V1_2::ContaminantDetectionStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::V1_2::ContaminantDetectionStatus::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    if (o == ::android::hardware::usb::V1_2::ContaminantDetectionStatus::DISABLED) {
        return "DISABLED";
    }
    if (o == ::android::hardware::usb::V1_2::ContaminantDetectionStatus::NOT_DETECTED) {
        return "NOT_DETECTED";
    }
    if (o == ::android::hardware::usb::V1_2::ContaminantDetectionStatus::DETECTED) {
        return "DETECTED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::V1_2::ContaminantDetectionStatus o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::usb::V1_2::ContaminantProtectionMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_2::ContaminantProtectionMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::V1_2::ContaminantProtectionMode::NONE) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantProtectionMode::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantProtectionMode::NONE;
    }
    if ((o & ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_SINK) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_SINK)) {
        os += (first ? "" : " | ");
        os += "FORCE_SINK";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_SINK;
    }
    if ((o & ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_SOURCE) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_SOURCE)) {
        os += (first ? "" : " | ");
        os += "FORCE_SOURCE";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_SOURCE;
    }
    if ((o & ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_DISABLE) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_DISABLE)) {
        os += (first ? "" : " | ");
        os += "FORCE_DISABLE";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_DISABLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::V1_2::ContaminantProtectionMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::V1_2::ContaminantProtectionMode::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_SINK) {
        return "FORCE_SINK";
    }
    if (o == ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_SOURCE) {
        return "FORCE_SOURCE";
    }
    if (o == ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_DISABLE) {
        return "FORCE_DISABLE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::V1_2::ContaminantProtectionMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::usb::V1_2::ContaminantProtectionStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_2::ContaminantProtectionStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::V1_2::ContaminantProtectionStatus::NONE) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantProtectionStatus::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantProtectionStatus::NONE;
    }
    if ((o & ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_SINK) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_SINK)) {
        os += (first ? "" : " | ");
        os += "FORCE_SINK";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_SINK;
    }
    if ((o & ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_SOURCE) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_SOURCE)) {
        os += (first ? "" : " | ");
        os += "FORCE_SOURCE";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_SOURCE;
    }
    if ((o & ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_DISABLE) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_DISABLE)) {
        os += (first ? "" : " | ");
        os += "FORCE_DISABLE";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_DISABLE;
    }
    if ((o & ::android::hardware::usb::V1_2::ContaminantProtectionStatus::DISABLED) == static_cast<uint32_t>(::android::hardware::usb::V1_2::ContaminantProtectionStatus::DISABLED)) {
        os += (first ? "" : " | ");
        os += "DISABLED";
        first = false;
        flipped |= ::android::hardware::usb::V1_2::ContaminantProtectionStatus::DISABLED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::V1_2::ContaminantProtectionStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::V1_2::ContaminantProtectionStatus::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_SINK) {
        return "FORCE_SINK";
    }
    if (o == ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_SOURCE) {
        return "FORCE_SOURCE";
    }
    if (o == ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_DISABLE) {
        return "FORCE_DISABLE";
    }
    if (o == ::android::hardware::usb::V1_2::ContaminantProtectionStatus::DISABLED) {
        return "DISABLED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::V1_2::ContaminantProtectionStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::usb::V1_2::PortStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status_1_1 = ";
    os += ::android::hardware::usb::V1_1::toString(o.status_1_1);
    os += ", .supportedContaminantProtectionModes = ";
    os += ::android::hardware::usb::V1_2::toString<::android::hardware::usb::V1_2::ContaminantProtectionMode>(o.supportedContaminantProtectionModes);
    os += ", .supportsEnableContaminantPresenceProtection = ";
    os += ::android::hardware::toString(o.supportsEnableContaminantPresenceProtection);
    os += ", .contaminantProtectionStatus = ";
    os += ::android::hardware::usb::V1_2::toString(o.contaminantProtectionStatus);
    os += ", .supportsEnableContaminantPresenceDetection = ";
    os += ::android::hardware::toString(o.supportsEnableContaminantPresenceDetection);
    os += ", .contaminantDetectionStatus = ";
    os += ::android::hardware::usb::V1_2::toString(o.contaminantDetectionStatus);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::usb::V1_2::PortStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::usb::V1_2::PortStatus& lhs, const ::android::hardware::usb::V1_2::PortStatus& rhs) {
    if (lhs.status_1_1 != rhs.status_1_1) {
        return false;
    }
    if (lhs.supportedContaminantProtectionModes != rhs.supportedContaminantProtectionModes) {
        return false;
    }
    if (lhs.supportsEnableContaminantPresenceProtection != rhs.supportsEnableContaminantPresenceProtection) {
        return false;
    }
    if (lhs.contaminantProtectionStatus != rhs.contaminantProtectionStatus) {
        return false;
    }
    if (lhs.supportsEnableContaminantPresenceDetection != rhs.supportsEnableContaminantPresenceDetection) {
        return false;
    }
    if (lhs.contaminantDetectionStatus != rhs.contaminantDetectionStatus) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::usb::V1_2::PortStatus& lhs, const ::android::hardware::usb::V1_2::PortStatus& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_2
}  // namespace usb
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
template<> inline constexpr std::array<::android::hardware::usb::V1_2::Status, 5> hidl_enum_values<::android::hardware::usb::V1_2::Status> = {
    ::android::hardware::usb::V1_2::Status::SUCCESS,
    ::android::hardware::usb::V1_2::Status::ERROR,
    ::android::hardware::usb::V1_2::Status::INVALID_ARGUMENT,
    ::android::hardware::usb::V1_2::Status::UNRECOGNIZED_ROLE,
    ::android::hardware::usb::V1_2::Status::NOT_SUPPORTED,
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
template<> inline constexpr std::array<::android::hardware::usb::V1_2::ContaminantDetectionStatus, 4> hidl_enum_values<::android::hardware::usb::V1_2::ContaminantDetectionStatus> = {
    ::android::hardware::usb::V1_2::ContaminantDetectionStatus::NOT_SUPPORTED,
    ::android::hardware::usb::V1_2::ContaminantDetectionStatus::DISABLED,
    ::android::hardware::usb::V1_2::ContaminantDetectionStatus::NOT_DETECTED,
    ::android::hardware::usb::V1_2::ContaminantDetectionStatus::DETECTED,
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
template<> inline constexpr std::array<::android::hardware::usb::V1_2::ContaminantProtectionMode, 4> hidl_enum_values<::android::hardware::usb::V1_2::ContaminantProtectionMode> = {
    ::android::hardware::usb::V1_2::ContaminantProtectionMode::NONE,
    ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_SINK,
    ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_SOURCE,
    ::android::hardware::usb::V1_2::ContaminantProtectionMode::FORCE_DISABLE,
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
template<> inline constexpr std::array<::android::hardware::usb::V1_2::ContaminantProtectionStatus, 5> hidl_enum_values<::android::hardware::usb::V1_2::ContaminantProtectionStatus> = {
    ::android::hardware::usb::V1_2::ContaminantProtectionStatus::NONE,
    ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_SINK,
    ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_SOURCE,
    ::android::hardware::usb::V1_2::ContaminantProtectionStatus::FORCE_DISABLE,
    ::android::hardware::usb::V1_2::ContaminantProtectionStatus::DISABLED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_2_TYPES_H
