#ifndef HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace thermal {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class TemperatureType : int32_t;
enum class CoolingType : uint32_t;
struct Temperature;
struct CoolingDevice;
struct CpuUsage;
enum class ThermalStatusCode : uint32_t;
struct ThermalStatus;

/**
 * Device temperature types
 */
enum class TemperatureType : int32_t {
    UNKNOWN = -1 /* -1 */,
    CPU = 0,
    GPU = 1,
    BATTERY = 2,
    SKIN = 3,
};

enum class CoolingType : uint32_t {
    /**
     * Fan cooling device speed in RPM.
     */
    FAN_RPM = 0u,
};

struct Temperature final {
    /**
     * This temperature's type.
     */
    ::android::hardware::thermal::V1_0::TemperatureType type __attribute__ ((aligned(4)));
    /**
     * Name of this temperature.
     * All temperatures of the same "type" must have a different "name",
     * e.g., cpu0, battery.
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * Current temperature in Celsius. If not available set by HAL to NAN.
     * Current temperature can be in any units if type=UNKNOWN.
     */
    float currentValue __attribute__ ((aligned(4)));
    /**
     * Throttling temperature constant for this temperature.
     * If not available, set by HAL to NAN.
     */
    float throttlingThreshold __attribute__ ((aligned(4)));
    /**
     * Shutdown temperature constant for this temperature.
     * If not available, set by HAL to NAN.
     */
    float shutdownThreshold __attribute__ ((aligned(4)));
    /**
     * Threshold temperature above which the VR mode clockrate minimums cannot
     * be maintained for this device.
     * If not available, set by HAL to NAN.
     */
    float vrThrottlingThreshold __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::thermal::V1_0::Temperature, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::Temperature, name) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::Temperature, currentValue) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::Temperature, throttlingThreshold) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::Temperature, shutdownThreshold) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::Temperature, vrThrottlingThreshold) == 36, "wrong offset");
static_assert(sizeof(::android::hardware::thermal::V1_0::Temperature) == 40, "wrong size");
static_assert(__alignof(::android::hardware::thermal::V1_0::Temperature) == 8, "wrong alignment");

struct CoolingDevice final {
    /**
     * This cooling device type.
     */
    ::android::hardware::thermal::V1_0::CoolingType type __attribute__ ((aligned(4)));
    /**
     * Name of this cooling device.
     * All cooling devices of the same "type" must have a different "name".
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * Current cooling device value. Units depend on cooling device "type".
     */
    float currentValue __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::thermal::V1_0::CoolingDevice, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::CoolingDevice, name) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::CoolingDevice, currentValue) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::thermal::V1_0::CoolingDevice) == 32, "wrong size");
static_assert(__alignof(::android::hardware::thermal::V1_0::CoolingDevice) == 8, "wrong alignment");

struct CpuUsage final {
    /**
     * Name of this CPU.
     * All CPUs must have a different "name".
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * Active time since the last boot in ms.
     */
    uint64_t active __attribute__ ((aligned(8)));
    /**
     * Total time since the last boot in ms.
     */
    uint64_t total __attribute__ ((aligned(8)));
    /**
     * Is set to true when a core is online.
     * If the core is offline, all other members except |name| should be ignored.
     */
    bool isOnline __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::thermal::V1_0::CpuUsage, name) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::CpuUsage, active) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::CpuUsage, total) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::CpuUsage, isOnline) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::thermal::V1_0::CpuUsage) == 40, "wrong size");
static_assert(__alignof(::android::hardware::thermal::V1_0::CpuUsage) == 8, "wrong alignment");

enum class ThermalStatusCode : uint32_t {
    /**
     * No errors.
     */
    SUCCESS = 0u,
    /**
     * Unknown failure occured.
     */
    FAILURE = 1u,
};

/**
 * Generic structure to return the status of any thermal operation.
 */
struct ThermalStatus final {
    ::android::hardware::thermal::V1_0::ThermalStatusCode code __attribute__ ((aligned(4)));
    /**
     * A specific error message to provide more information.
     * This can be used for debugging purposes only.
     */
    ::android::hardware::hidl_string debugMessage __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::thermal::V1_0::ThermalStatus, code) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::thermal::V1_0::ThermalStatus, debugMessage) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::thermal::V1_0::ThermalStatus) == 24, "wrong size");
static_assert(__alignof(::android::hardware::thermal::V1_0::ThermalStatus) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::thermal::V1_0::TemperatureType o);
static inline void PrintTo(::android::hardware::thermal::V1_0::TemperatureType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::thermal::V1_0::TemperatureType lhs, const ::android::hardware::thermal::V1_0::TemperatureType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::thermal::V1_0::TemperatureType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::thermal::V1_0::TemperatureType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::thermal::V1_0::TemperatureType lhs, const ::android::hardware::thermal::V1_0::TemperatureType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::thermal::V1_0::TemperatureType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::thermal::V1_0::TemperatureType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::thermal::V1_0::TemperatureType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::thermal::V1_0::TemperatureType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::thermal::V1_0::CoolingType o);
static inline void PrintTo(::android::hardware::thermal::V1_0::CoolingType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::thermal::V1_0::CoolingType lhs, const ::android::hardware::thermal::V1_0::CoolingType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::thermal::V1_0::CoolingType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::thermal::V1_0::CoolingType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::thermal::V1_0::CoolingType lhs, const ::android::hardware::thermal::V1_0::CoolingType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::thermal::V1_0::CoolingType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::thermal::V1_0::CoolingType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::thermal::V1_0::CoolingType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::thermal::V1_0::CoolingType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::thermal::V1_0::Temperature& o);
static inline void PrintTo(const ::android::hardware::thermal::V1_0::Temperature& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::thermal::V1_0::Temperature& lhs, const ::android::hardware::thermal::V1_0::Temperature& rhs);
static inline bool operator!=(const ::android::hardware::thermal::V1_0::Temperature& lhs, const ::android::hardware::thermal::V1_0::Temperature& rhs);

static inline std::string toString(const ::android::hardware::thermal::V1_0::CoolingDevice& o);
static inline void PrintTo(const ::android::hardware::thermal::V1_0::CoolingDevice& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::thermal::V1_0::CoolingDevice& lhs, const ::android::hardware::thermal::V1_0::CoolingDevice& rhs);
static inline bool operator!=(const ::android::hardware::thermal::V1_0::CoolingDevice& lhs, const ::android::hardware::thermal::V1_0::CoolingDevice& rhs);

static inline std::string toString(const ::android::hardware::thermal::V1_0::CpuUsage& o);
static inline void PrintTo(const ::android::hardware::thermal::V1_0::CpuUsage& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::thermal::V1_0::CpuUsage& lhs, const ::android::hardware::thermal::V1_0::CpuUsage& rhs);
static inline bool operator!=(const ::android::hardware::thermal::V1_0::CpuUsage& lhs, const ::android::hardware::thermal::V1_0::CpuUsage& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::thermal::V1_0::ThermalStatusCode o);
static inline void PrintTo(::android::hardware::thermal::V1_0::ThermalStatusCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::thermal::V1_0::ThermalStatusCode lhs, const ::android::hardware::thermal::V1_0::ThermalStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::thermal::V1_0::ThermalStatusCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::thermal::V1_0::ThermalStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::thermal::V1_0::ThermalStatusCode lhs, const ::android::hardware::thermal::V1_0::ThermalStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::thermal::V1_0::ThermalStatusCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::thermal::V1_0::ThermalStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::thermal::V1_0::ThermalStatusCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::thermal::V1_0::ThermalStatusCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::thermal::V1_0::ThermalStatus& o);
static inline void PrintTo(const ::android::hardware::thermal::V1_0::ThermalStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::thermal::V1_0::ThermalStatus& lhs, const ::android::hardware::thermal::V1_0::ThermalStatus& rhs);
static inline bool operator!=(const ::android::hardware::thermal::V1_0::ThermalStatus& lhs, const ::android::hardware::thermal::V1_0::ThermalStatus& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::thermal::V1_0::TemperatureType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::thermal::V1_0::TemperatureType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::thermal::V1_0::TemperatureType::UNKNOWN) == static_cast<int32_t>(::android::hardware::thermal::V1_0::TemperatureType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::thermal::V1_0::TemperatureType::UNKNOWN;
    }
    if ((o & ::android::hardware::thermal::V1_0::TemperatureType::CPU) == static_cast<int32_t>(::android::hardware::thermal::V1_0::TemperatureType::CPU)) {
        os += (first ? "" : " | ");
        os += "CPU";
        first = false;
        flipped |= ::android::hardware::thermal::V1_0::TemperatureType::CPU;
    }
    if ((o & ::android::hardware::thermal::V1_0::TemperatureType::GPU) == static_cast<int32_t>(::android::hardware::thermal::V1_0::TemperatureType::GPU)) {
        os += (first ? "" : " | ");
        os += "GPU";
        first = false;
        flipped |= ::android::hardware::thermal::V1_0::TemperatureType::GPU;
    }
    if ((o & ::android::hardware::thermal::V1_0::TemperatureType::BATTERY) == static_cast<int32_t>(::android::hardware::thermal::V1_0::TemperatureType::BATTERY)) {
        os += (first ? "" : " | ");
        os += "BATTERY";
        first = false;
        flipped |= ::android::hardware::thermal::V1_0::TemperatureType::BATTERY;
    }
    if ((o & ::android::hardware::thermal::V1_0::TemperatureType::SKIN) == static_cast<int32_t>(::android::hardware::thermal::V1_0::TemperatureType::SKIN)) {
        os += (first ? "" : " | ");
        os += "SKIN";
        first = false;
        flipped |= ::android::hardware::thermal::V1_0::TemperatureType::SKIN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::thermal::V1_0::TemperatureType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::thermal::V1_0::TemperatureType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::thermal::V1_0::TemperatureType::CPU) {
        return "CPU";
    }
    if (o == ::android::hardware::thermal::V1_0::TemperatureType::GPU) {
        return "GPU";
    }
    if (o == ::android::hardware::thermal::V1_0::TemperatureType::BATTERY) {
        return "BATTERY";
    }
    if (o == ::android::hardware::thermal::V1_0::TemperatureType::SKIN) {
        return "SKIN";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::thermal::V1_0::TemperatureType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::thermal::V1_0::CoolingType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::thermal::V1_0::CoolingType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::thermal::V1_0::CoolingType::FAN_RPM) == static_cast<uint32_t>(::android::hardware::thermal::V1_0::CoolingType::FAN_RPM)) {
        os += (first ? "" : " | ");
        os += "FAN_RPM";
        first = false;
        flipped |= ::android::hardware::thermal::V1_0::CoolingType::FAN_RPM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::thermal::V1_0::CoolingType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::thermal::V1_0::CoolingType::FAN_RPM) {
        return "FAN_RPM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::thermal::V1_0::CoolingType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::thermal::V1_0::Temperature& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::thermal::V1_0::toString(o.type);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .currentValue = ";
    os += ::android::hardware::toString(o.currentValue);
    os += ", .throttlingThreshold = ";
    os += ::android::hardware::toString(o.throttlingThreshold);
    os += ", .shutdownThreshold = ";
    os += ::android::hardware::toString(o.shutdownThreshold);
    os += ", .vrThrottlingThreshold = ";
    os += ::android::hardware::toString(o.vrThrottlingThreshold);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::thermal::V1_0::Temperature& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::thermal::V1_0::Temperature& lhs, const ::android::hardware::thermal::V1_0::Temperature& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.currentValue != rhs.currentValue) {
        return false;
    }
    if (lhs.throttlingThreshold != rhs.throttlingThreshold) {
        return false;
    }
    if (lhs.shutdownThreshold != rhs.shutdownThreshold) {
        return false;
    }
    if (lhs.vrThrottlingThreshold != rhs.vrThrottlingThreshold) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::thermal::V1_0::Temperature& lhs, const ::android::hardware::thermal::V1_0::Temperature& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::thermal::V1_0::CoolingDevice& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::thermal::V1_0::toString(o.type);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .currentValue = ";
    os += ::android::hardware::toString(o.currentValue);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::thermal::V1_0::CoolingDevice& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::thermal::V1_0::CoolingDevice& lhs, const ::android::hardware::thermal::V1_0::CoolingDevice& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.currentValue != rhs.currentValue) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::thermal::V1_0::CoolingDevice& lhs, const ::android::hardware::thermal::V1_0::CoolingDevice& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::thermal::V1_0::CpuUsage& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .active = ";
    os += ::android::hardware::toString(o.active);
    os += ", .total = ";
    os += ::android::hardware::toString(o.total);
    os += ", .isOnline = ";
    os += ::android::hardware::toString(o.isOnline);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::thermal::V1_0::CpuUsage& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::thermal::V1_0::CpuUsage& lhs, const ::android::hardware::thermal::V1_0::CpuUsage& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.active != rhs.active) {
        return false;
    }
    if (lhs.total != rhs.total) {
        return false;
    }
    if (lhs.isOnline != rhs.isOnline) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::thermal::V1_0::CpuUsage& lhs, const ::android::hardware::thermal::V1_0::CpuUsage& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::thermal::V1_0::ThermalStatusCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::thermal::V1_0::ThermalStatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::thermal::V1_0::ThermalStatusCode::SUCCESS) == static_cast<uint32_t>(::android::hardware::thermal::V1_0::ThermalStatusCode::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::thermal::V1_0::ThermalStatusCode::SUCCESS;
    }
    if ((o & ::android::hardware::thermal::V1_0::ThermalStatusCode::FAILURE) == static_cast<uint32_t>(::android::hardware::thermal::V1_0::ThermalStatusCode::FAILURE)) {
        os += (first ? "" : " | ");
        os += "FAILURE";
        first = false;
        flipped |= ::android::hardware::thermal::V1_0::ThermalStatusCode::FAILURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::thermal::V1_0::ThermalStatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::thermal::V1_0::ThermalStatusCode::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::thermal::V1_0::ThermalStatusCode::FAILURE) {
        return "FAILURE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::thermal::V1_0::ThermalStatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::thermal::V1_0::ThermalStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".code = ";
    os += ::android::hardware::thermal::V1_0::toString(o.code);
    os += ", .debugMessage = ";
    os += ::android::hardware::toString(o.debugMessage);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::thermal::V1_0::ThermalStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::thermal::V1_0::ThermalStatus& lhs, const ::android::hardware::thermal::V1_0::ThermalStatus& rhs) {
    if (lhs.code != rhs.code) {
        return false;
    }
    if (lhs.debugMessage != rhs.debugMessage) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::thermal::V1_0::ThermalStatus& lhs, const ::android::hardware::thermal::V1_0::ThermalStatus& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace thermal
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
template<> inline constexpr std::array<::android::hardware::thermal::V1_0::TemperatureType, 5> hidl_enum_values<::android::hardware::thermal::V1_0::TemperatureType> = {
    ::android::hardware::thermal::V1_0::TemperatureType::UNKNOWN,
    ::android::hardware::thermal::V1_0::TemperatureType::CPU,
    ::android::hardware::thermal::V1_0::TemperatureType::GPU,
    ::android::hardware::thermal::V1_0::TemperatureType::BATTERY,
    ::android::hardware::thermal::V1_0::TemperatureType::SKIN,
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
template<> inline constexpr std::array<::android::hardware::thermal::V1_0::CoolingType, 1> hidl_enum_values<::android::hardware::thermal::V1_0::CoolingType> = {
    ::android::hardware::thermal::V1_0::CoolingType::FAN_RPM,
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
template<> inline constexpr std::array<::android::hardware::thermal::V1_0::ThermalStatusCode, 2> hidl_enum_values<::android::hardware::thermal::V1_0::ThermalStatusCode> = {
    ::android::hardware::thermal::V1_0::ThermalStatusCode::SUCCESS,
    ::android::hardware::thermal::V1_0::ThermalStatusCode::FAILURE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V1_0_TYPES_H
