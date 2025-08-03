#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace usb {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;
enum class PortRoleType : uint32_t;
enum class PortDataRole : uint32_t;
enum class PortPowerRole : uint32_t;
enum class PortMode : uint32_t;
struct PortRole;
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
};

/**
 * Denotes the Port role type.
 * Passed as an argument for functions used to query or change port roles.
 */
enum class PortRoleType : uint32_t {
    /**
     * Denotes the data role of the port.
     * The port can either be a "host" or a "device" for data.
     * This maps to the PortDataRole enum.
     */
    DATA_ROLE = 0u,
    /**
     * Denotes the power role of the port.
     * The port can either be a "source" or "sink" for power.
     * This maps to PortPowerRole enum.
     */
    POWER_ROLE = 1u,
    /**
     * USB ports can be a pure DFP port which can only act
     * as a host. A UFP port which can only act as a device.
     * Or a dual role ports which can either can as a host or
     * a device. This property is used to mention them.
     */
    MODE = 2u,
};

enum class PortDataRole : uint32_t {
    /**
     * Indicates that the port does not have a data role.
     * In case of DRP, the current data role of the port is only resolved
     * when the type-c handshake happens.
     */
    NONE = 0u,
    /**
     * Indicates that the port is acting as a host for data.
     */
    HOST = 1u,
    /**
     * Indicated that the port is acting as a device for data.
     */
    DEVICE = 2u,
    NUM_DATA_ROLES = 3u,
};

enum class PortPowerRole : uint32_t {
    /**
     * Indicates that the port does not have a power role.
     * In case of DRP, the current power role of the port is only resolved
     * when the type-c handshake happens.
     */
    NONE = 0u,
    /**
     * Indicates that the port is supplying power to the other port.
     */
    SOURCE = 1u,
    /**
     * Indicates that the port is sinking power from the other port.
     */
    SINK = 2u,
    NUM_POWER_ROLES = 3u,
};

enum class PortMode : uint32_t {
    /**
     * Indicates that the port does not have a mode.
     * In case of DRP, the current mode of the port is only resolved
     * when the type-c handshake happens.
     */
    NONE = 0u,
    /**
     * Indicates that port can only act as device for data and sink for power.
     */
    UFP = 1u,
    /**
     * Indicates the port can only act as host for data and source for power.
     */
    DFP = 2u,
    /**
     * Indicates can either act as UFP or DFP at a given point of time.
     */
    DRP = 3u,
    NUM_MODES = 4u,
};

/**
 * Used as a container to send port role information.
 */
struct PortRole final {
    /**
     * Indicates the type of Port Role.
     * Maps to the PortRoleType enum.
     */
    ::android::hardware::usb::V1_0::PortRoleType type __attribute__ ((aligned(4)));
    /**
     * when type is HAL_USB_DATA_ROLE pass values from enum PortDataRole.
     * when type is HAL_USB_POWER_ROLE pass values from enum PortPowerRole.
     * when type is HAL_USB_MODE pass values from enum PortMode.
     */
    uint32_t role __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::usb::V1_0::PortRole, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_0::PortRole, role) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::usb::V1_0::PortRole) == 8, "wrong size");
static_assert(__alignof(::android::hardware::usb::V1_0::PortRole) == 4, "wrong alignment");

/**
 * Used as the container to report data back to the caller.
 * Represents the current connection status of a single USB port.
 */
struct PortStatus final {
    /**
     * Name of the port.
     * Used as the port's id by the caller.
     */
    ::android::hardware::hidl_string portName __attribute__ ((aligned(8)));
    /**
     * Data role of the port.
     */
    ::android::hardware::usb::V1_0::PortDataRole currentDataRole __attribute__ ((aligned(4)));
    /**
     * Power Role of thte port.
     */
    ::android::hardware::usb::V1_0::PortPowerRole currentPowerRole __attribute__ ((aligned(4)));
    /**
     * Mode in which the port is connected.
     * Can be UFP or DFP.
     */
    ::android::hardware::usb::V1_0::PortMode currentMode __attribute__ ((aligned(4)));
    /**
     * True indicates that the port's mode can
     * be changed. False otherwise.
     */
    bool canChangeMode __attribute__ ((aligned(1)));
    /**
     * True indicates that the port's data role
     * can be changed. False otherwise.
     * For example, true if Type-C PD PD_SWAP
     * is supported.
     */
    bool canChangeDataRole __attribute__ ((aligned(1)));
    /**
     * True indicates that the port's power role
     * can be changed. False otherwise.
     * For example, true if Type-C PD PR_SWAP
     * is supported.
     */
    bool canChangePowerRole __attribute__ ((aligned(1)));
    /**
     * Identifies the type of the local port.
     *
     * UFP - Indicates that port can only act as device for
     *       data and sink for power.
     * DFP - Indicates the port can only act as host for data
     *       and source for power.
     * DRP - Indicates can either act as UFP or DFP at a
     *       given point of time.
     */
    ::android::hardware::usb::V1_0::PortMode supportedModes __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::usb::V1_0::PortStatus, portName) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_0::PortStatus, currentDataRole) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_0::PortStatus, currentPowerRole) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_0::PortStatus, currentMode) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_0::PortStatus, canChangeMode) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_0::PortStatus, canChangeDataRole) == 29, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_0::PortStatus, canChangePowerRole) == 30, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_0::PortStatus, supportedModes) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::usb::V1_0::PortStatus) == 40, "wrong size");
static_assert(__alignof(::android::hardware::usb::V1_0::PortStatus) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::V1_0::Status o);
static inline void PrintTo(::android::hardware::usb::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::V1_0::Status lhs, const ::android::hardware::usb::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_0::Status lhs, const ::android::hardware::usb::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::V1_0::PortRoleType o);
static inline void PrintTo(::android::hardware::usb::V1_0::PortRoleType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::V1_0::PortRoleType lhs, const ::android::hardware::usb::V1_0::PortRoleType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::V1_0::PortRoleType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::V1_0::PortRoleType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_0::PortRoleType lhs, const ::android::hardware::usb::V1_0::PortRoleType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::V1_0::PortRoleType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_0::PortRoleType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::V1_0::PortRoleType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::V1_0::PortRoleType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::V1_0::PortDataRole o);
static inline void PrintTo(::android::hardware::usb::V1_0::PortDataRole o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::V1_0::PortDataRole lhs, const ::android::hardware::usb::V1_0::PortDataRole rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::V1_0::PortDataRole rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::V1_0::PortDataRole lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_0::PortDataRole lhs, const ::android::hardware::usb::V1_0::PortDataRole rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::V1_0::PortDataRole rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_0::PortDataRole lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::V1_0::PortDataRole e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::V1_0::PortDataRole e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::V1_0::PortPowerRole o);
static inline void PrintTo(::android::hardware::usb::V1_0::PortPowerRole o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::V1_0::PortPowerRole lhs, const ::android::hardware::usb::V1_0::PortPowerRole rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::V1_0::PortPowerRole rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::V1_0::PortPowerRole lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_0::PortPowerRole lhs, const ::android::hardware::usb::V1_0::PortPowerRole rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::V1_0::PortPowerRole rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_0::PortPowerRole lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::V1_0::PortPowerRole e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::V1_0::PortPowerRole e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::V1_0::PortMode o);
static inline void PrintTo(::android::hardware::usb::V1_0::PortMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::V1_0::PortMode lhs, const ::android::hardware::usb::V1_0::PortMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::V1_0::PortMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::V1_0::PortMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_0::PortMode lhs, const ::android::hardware::usb::V1_0::PortMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::V1_0::PortMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_0::PortMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::V1_0::PortMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::V1_0::PortMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::usb::V1_0::PortRole& o);
static inline void PrintTo(const ::android::hardware::usb::V1_0::PortRole& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::usb::V1_0::PortRole& lhs, const ::android::hardware::usb::V1_0::PortRole& rhs);
static inline bool operator!=(const ::android::hardware::usb::V1_0::PortRole& lhs, const ::android::hardware::usb::V1_0::PortRole& rhs);

static inline std::string toString(const ::android::hardware::usb::V1_0::PortStatus& o);
static inline void PrintTo(const ::android::hardware::usb::V1_0::PortStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::usb::V1_0::PortStatus& lhs, const ::android::hardware::usb::V1_0::PortStatus& rhs);
static inline bool operator!=(const ::android::hardware::usb::V1_0::PortStatus& lhs, const ::android::hardware::usb::V1_0::PortStatus& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::usb::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::V1_0::Status::SUCCESS) == static_cast<uint32_t>(::android::hardware::usb::V1_0::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::Status::SUCCESS;
    }
    if ((o & ::android::hardware::usb::V1_0::Status::ERROR) == static_cast<uint32_t>(::android::hardware::usb::V1_0::Status::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::Status::ERROR;
    }
    if ((o & ::android::hardware::usb::V1_0::Status::INVALID_ARGUMENT) == static_cast<uint32_t>(::android::hardware::usb::V1_0::Status::INVALID_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENT";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::Status::INVALID_ARGUMENT;
    }
    if ((o & ::android::hardware::usb::V1_0::Status::UNRECOGNIZED_ROLE) == static_cast<uint32_t>(::android::hardware::usb::V1_0::Status::UNRECOGNIZED_ROLE)) {
        os += (first ? "" : " | ");
        os += "UNRECOGNIZED_ROLE";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::Status::UNRECOGNIZED_ROLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::V1_0::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::usb::V1_0::Status::ERROR) {
        return "ERROR";
    }
    if (o == ::android::hardware::usb::V1_0::Status::INVALID_ARGUMENT) {
        return "INVALID_ARGUMENT";
    }
    if (o == ::android::hardware::usb::V1_0::Status::UNRECOGNIZED_ROLE) {
        return "UNRECOGNIZED_ROLE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::usb::V1_0::PortRoleType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_0::PortRoleType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::V1_0::PortRoleType::DATA_ROLE) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortRoleType::DATA_ROLE)) {
        os += (first ? "" : " | ");
        os += "DATA_ROLE";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortRoleType::DATA_ROLE;
    }
    if ((o & ::android::hardware::usb::V1_0::PortRoleType::POWER_ROLE) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortRoleType::POWER_ROLE)) {
        os += (first ? "" : " | ");
        os += "POWER_ROLE";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortRoleType::POWER_ROLE;
    }
    if ((o & ::android::hardware::usb::V1_0::PortRoleType::MODE) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortRoleType::MODE)) {
        os += (first ? "" : " | ");
        os += "MODE";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortRoleType::MODE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::V1_0::PortRoleType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::V1_0::PortRoleType::DATA_ROLE) {
        return "DATA_ROLE";
    }
    if (o == ::android::hardware::usb::V1_0::PortRoleType::POWER_ROLE) {
        return "POWER_ROLE";
    }
    if (o == ::android::hardware::usb::V1_0::PortRoleType::MODE) {
        return "MODE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::V1_0::PortRoleType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::usb::V1_0::PortDataRole>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_0::PortDataRole> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::V1_0::PortDataRole::NONE) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortDataRole::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortDataRole::NONE;
    }
    if ((o & ::android::hardware::usb::V1_0::PortDataRole::HOST) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortDataRole::HOST)) {
        os += (first ? "" : " | ");
        os += "HOST";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortDataRole::HOST;
    }
    if ((o & ::android::hardware::usb::V1_0::PortDataRole::DEVICE) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortDataRole::DEVICE)) {
        os += (first ? "" : " | ");
        os += "DEVICE";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortDataRole::DEVICE;
    }
    if ((o & ::android::hardware::usb::V1_0::PortDataRole::NUM_DATA_ROLES) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortDataRole::NUM_DATA_ROLES)) {
        os += (first ? "" : " | ");
        os += "NUM_DATA_ROLES";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortDataRole::NUM_DATA_ROLES;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::V1_0::PortDataRole o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::V1_0::PortDataRole::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::usb::V1_0::PortDataRole::HOST) {
        return "HOST";
    }
    if (o == ::android::hardware::usb::V1_0::PortDataRole::DEVICE) {
        return "DEVICE";
    }
    if (o == ::android::hardware::usb::V1_0::PortDataRole::NUM_DATA_ROLES) {
        return "NUM_DATA_ROLES";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::V1_0::PortDataRole o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::usb::V1_0::PortPowerRole>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_0::PortPowerRole> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::V1_0::PortPowerRole::NONE) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortPowerRole::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortPowerRole::NONE;
    }
    if ((o & ::android::hardware::usb::V1_0::PortPowerRole::SOURCE) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortPowerRole::SOURCE)) {
        os += (first ? "" : " | ");
        os += "SOURCE";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortPowerRole::SOURCE;
    }
    if ((o & ::android::hardware::usb::V1_0::PortPowerRole::SINK) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortPowerRole::SINK)) {
        os += (first ? "" : " | ");
        os += "SINK";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortPowerRole::SINK;
    }
    if ((o & ::android::hardware::usb::V1_0::PortPowerRole::NUM_POWER_ROLES) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortPowerRole::NUM_POWER_ROLES)) {
        os += (first ? "" : " | ");
        os += "NUM_POWER_ROLES";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortPowerRole::NUM_POWER_ROLES;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::V1_0::PortPowerRole o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::V1_0::PortPowerRole::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::usb::V1_0::PortPowerRole::SOURCE) {
        return "SOURCE";
    }
    if (o == ::android::hardware::usb::V1_0::PortPowerRole::SINK) {
        return "SINK";
    }
    if (o == ::android::hardware::usb::V1_0::PortPowerRole::NUM_POWER_ROLES) {
        return "NUM_POWER_ROLES";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::V1_0::PortPowerRole o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::usb::V1_0::PortMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_0::PortMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::V1_0::PortMode::NONE) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortMode::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortMode::NONE;
    }
    if ((o & ::android::hardware::usb::V1_0::PortMode::UFP) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortMode::UFP)) {
        os += (first ? "" : " | ");
        os += "UFP";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortMode::UFP;
    }
    if ((o & ::android::hardware::usb::V1_0::PortMode::DFP) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortMode::DFP)) {
        os += (first ? "" : " | ");
        os += "DFP";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortMode::DFP;
    }
    if ((o & ::android::hardware::usb::V1_0::PortMode::DRP) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortMode::DRP)) {
        os += (first ? "" : " | ");
        os += "DRP";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortMode::DRP;
    }
    if ((o & ::android::hardware::usb::V1_0::PortMode::NUM_MODES) == static_cast<uint32_t>(::android::hardware::usb::V1_0::PortMode::NUM_MODES)) {
        os += (first ? "" : " | ");
        os += "NUM_MODES";
        first = false;
        flipped |= ::android::hardware::usb::V1_0::PortMode::NUM_MODES;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::V1_0::PortMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::V1_0::PortMode::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::usb::V1_0::PortMode::UFP) {
        return "UFP";
    }
    if (o == ::android::hardware::usb::V1_0::PortMode::DFP) {
        return "DFP";
    }
    if (o == ::android::hardware::usb::V1_0::PortMode::DRP) {
        return "DRP";
    }
    if (o == ::android::hardware::usb::V1_0::PortMode::NUM_MODES) {
        return "NUM_MODES";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::V1_0::PortMode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::usb::V1_0::PortRole& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::usb::V1_0::toString(o.type);
    os += ", .role = ";
    os += ::android::hardware::toString(o.role);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::usb::V1_0::PortRole& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::usb::V1_0::PortRole& lhs, const ::android::hardware::usb::V1_0::PortRole& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.role != rhs.role) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::usb::V1_0::PortRole& lhs, const ::android::hardware::usb::V1_0::PortRole& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::usb::V1_0::PortStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".portName = ";
    os += ::android::hardware::toString(o.portName);
    os += ", .currentDataRole = ";
    os += ::android::hardware::usb::V1_0::toString(o.currentDataRole);
    os += ", .currentPowerRole = ";
    os += ::android::hardware::usb::V1_0::toString(o.currentPowerRole);
    os += ", .currentMode = ";
    os += ::android::hardware::usb::V1_0::toString(o.currentMode);
    os += ", .canChangeMode = ";
    os += ::android::hardware::toString(o.canChangeMode);
    os += ", .canChangeDataRole = ";
    os += ::android::hardware::toString(o.canChangeDataRole);
    os += ", .canChangePowerRole = ";
    os += ::android::hardware::toString(o.canChangePowerRole);
    os += ", .supportedModes = ";
    os += ::android::hardware::usb::V1_0::toString(o.supportedModes);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::usb::V1_0::PortStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::usb::V1_0::PortStatus& lhs, const ::android::hardware::usb::V1_0::PortStatus& rhs) {
    if (lhs.portName != rhs.portName) {
        return false;
    }
    if (lhs.currentDataRole != rhs.currentDataRole) {
        return false;
    }
    if (lhs.currentPowerRole != rhs.currentPowerRole) {
        return false;
    }
    if (lhs.currentMode != rhs.currentMode) {
        return false;
    }
    if (lhs.canChangeMode != rhs.canChangeMode) {
        return false;
    }
    if (lhs.canChangeDataRole != rhs.canChangeDataRole) {
        return false;
    }
    if (lhs.canChangePowerRole != rhs.canChangePowerRole) {
        return false;
    }
    if (lhs.supportedModes != rhs.supportedModes) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::usb::V1_0::PortStatus& lhs, const ::android::hardware::usb::V1_0::PortStatus& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
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
template<> inline constexpr std::array<::android::hardware::usb::V1_0::Status, 4> hidl_enum_values<::android::hardware::usb::V1_0::Status> = {
    ::android::hardware::usb::V1_0::Status::SUCCESS,
    ::android::hardware::usb::V1_0::Status::ERROR,
    ::android::hardware::usb::V1_0::Status::INVALID_ARGUMENT,
    ::android::hardware::usb::V1_0::Status::UNRECOGNIZED_ROLE,
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
template<> inline constexpr std::array<::android::hardware::usb::V1_0::PortRoleType, 3> hidl_enum_values<::android::hardware::usb::V1_0::PortRoleType> = {
    ::android::hardware::usb::V1_0::PortRoleType::DATA_ROLE,
    ::android::hardware::usb::V1_0::PortRoleType::POWER_ROLE,
    ::android::hardware::usb::V1_0::PortRoleType::MODE,
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
template<> inline constexpr std::array<::android::hardware::usb::V1_0::PortDataRole, 4> hidl_enum_values<::android::hardware::usb::V1_0::PortDataRole> = {
    ::android::hardware::usb::V1_0::PortDataRole::NONE,
    ::android::hardware::usb::V1_0::PortDataRole::HOST,
    ::android::hardware::usb::V1_0::PortDataRole::DEVICE,
    ::android::hardware::usb::V1_0::PortDataRole::NUM_DATA_ROLES,
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
template<> inline constexpr std::array<::android::hardware::usb::V1_0::PortPowerRole, 4> hidl_enum_values<::android::hardware::usb::V1_0::PortPowerRole> = {
    ::android::hardware::usb::V1_0::PortPowerRole::NONE,
    ::android::hardware::usb::V1_0::PortPowerRole::SOURCE,
    ::android::hardware::usb::V1_0::PortPowerRole::SINK,
    ::android::hardware::usb::V1_0::PortPowerRole::NUM_POWER_ROLES,
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
template<> inline constexpr std::array<::android::hardware::usb::V1_0::PortMode, 5> hidl_enum_values<::android::hardware::usb::V1_0::PortMode> = {
    ::android::hardware::usb::V1_0::PortMode::NONE,
    ::android::hardware::usb::V1_0::PortMode::UFP,
    ::android::hardware::usb::V1_0::PortMode::DFP,
    ::android::hardware::usb::V1_0::PortMode::DRP,
    ::android::hardware::usb::V1_0::PortMode::NUM_MODES,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_0_TYPES_H
