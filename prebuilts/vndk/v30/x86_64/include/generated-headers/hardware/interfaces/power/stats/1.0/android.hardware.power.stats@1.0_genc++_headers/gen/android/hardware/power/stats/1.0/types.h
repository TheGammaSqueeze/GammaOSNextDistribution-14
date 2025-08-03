#ifndef HIDL_GENERATED_ANDROID_HARDWARE_POWER_STATS_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_POWER_STATS_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace power {
namespace stats {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;
struct RailInfo;
struct EnergyData;
enum class PowerEntityType : uint32_t;
struct PowerEntityInfo;
struct PowerEntityStateInfo;
struct PowerEntityStateSpace;
struct PowerEntityStateResidencyData;
struct PowerEntityStateResidencyResult;

enum class Status : uint32_t {
    SUCCESS = 0u,
    NOT_SUPPORTED = 1u,
    INVALID_INPUT = 2u,
    FILESYSTEM_ERROR = 3u,
    INSUFFICIENT_RESOURCES = 4u,
};

struct RailInfo final {
    /**
     * Index corresponding to the rail
     */
    uint32_t index __attribute__ ((aligned(4)));
    /**
     * Name of the rail (opaque to the framework)
     */
    ::android::hardware::hidl_string railName __attribute__ ((aligned(8)));
    /**
     * Name of the subsystem to which this rail belongs (opaque to the framework)
     */
    ::android::hardware::hidl_string subsysName __attribute__ ((aligned(8)));
    /**
     * Hardware sampling rate
     */
    uint32_t samplingRate __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::power::stats::V1_0::RailInfo, index) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::RailInfo, railName) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::RailInfo, subsysName) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::RailInfo, samplingRate) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::power::stats::V1_0::RailInfo) == 48, "wrong size");
static_assert(__alignof(::android::hardware::power::stats::V1_0::RailInfo) == 8, "wrong alignment");

struct EnergyData final {
    /**
     * Index corresponding to the rail. This index matches
     * the index returned in RailInfo
     */
    uint32_t index __attribute__ ((aligned(4)));
    /**
     * Time since device boot(CLOCK_BOOTTIME) in milli-seconds
     */
    uint64_t timestamp __attribute__ ((aligned(8)));
    /**
     * Accumulated energy since device boot in microwatt-seconds (uWs)
     */
    uint64_t energy __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::power::stats::V1_0::EnergyData, index) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::EnergyData, timestamp) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::EnergyData, energy) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::power::stats::V1_0::EnergyData) == 24, "wrong size");
static_assert(__alignof(::android::hardware::power::stats::V1_0::EnergyData) == 8, "wrong alignment");

enum class PowerEntityType : uint32_t {
    /**
     * A subsystem is a self-contained compute unit. Some examples include
     * application processor, DSP, GPU.
     */
    SUBSYSTEM = 0u,
    /**
     * A peripheral is an auxiliary device that connects to and works with a
     * compute unit. Some examples include simple sensors, camera, display.
     */
    PERIPHERAL = 1u,
    /**
     * A power domain is a single subsystem or a collection of subsystems
     * that is controlled by a single voltage rail.
     */
    POWER_DOMAIN = 2u,
};

/**
 * PowerEntityInfo contains information, such as the ID, name, and type of a
 * given PowerEntity.
 */
struct PowerEntityInfo final {
    /**
     * Unique ID corresponding to the PowerEntity
     */
    uint32_t powerEntityId __attribute__ ((aligned(4)));
    /**
     * Name of the PowerEntity (opaque to the framework)
     */
    ::android::hardware::hidl_string powerEntityName __attribute__ ((aligned(8)));
    /**
     * Type of the PowerEntity
     */
    ::android::hardware::power::stats::V1_0::PowerEntityType type __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityInfo, powerEntityId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityInfo, powerEntityName) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityInfo, type) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::power::stats::V1_0::PowerEntityInfo) == 32, "wrong size");
static_assert(__alignof(::android::hardware::power::stats::V1_0::PowerEntityInfo) == 8, "wrong alignment");

struct PowerEntityStateInfo final {
    /**
     * ID corresponding to the state. Unique for a given PowerEntityStateSpace
     */
    uint32_t powerEntityStateId __attribute__ ((aligned(4)));
    /**
     * Name of the state (opaque to the framework)
     */
    ::android::hardware::hidl_string powerEntityStateName __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityStateInfo, powerEntityStateId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityStateInfo, powerEntityStateName) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::power::stats::V1_0::PowerEntityStateInfo) == 24, "wrong size");
static_assert(__alignof(::android::hardware::power::stats::V1_0::PowerEntityStateInfo) == 8, "wrong alignment");

/**
 * PowerEntityStateSpace contains the state space information of a given
 * PowerEntity. The state space, is the set of possible states that a given
 * PowerEntity provides residency data for.
 */
struct PowerEntityStateSpace final {
    /**
     * Unique ID of the corresponding PowerEntity
     */
    uint32_t powerEntityId __attribute__ ((aligned(4)));
    /**
     * List of states that the PowerEntity may reside in
     */
    ::android::hardware::hidl_vec<::android::hardware::power::stats::V1_0::PowerEntityStateInfo> states __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityStateSpace, powerEntityId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityStateSpace, states) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::power::stats::V1_0::PowerEntityStateSpace) == 24, "wrong size");
static_assert(__alignof(::android::hardware::power::stats::V1_0::PowerEntityStateSpace) == 8, "wrong alignment");

/**
 * Contains residency data for a single state
 */
struct PowerEntityStateResidencyData final {
    /**
     * Unique ID of the corresponding PowerEntityStateInfo
     */
    uint32_t powerEntityStateId __attribute__ ((aligned(4)));
    /**
     * Total time in milliseconds that the corresponding PowerEntity resided
     * in this state since the PowerEntity was reset
     */
    uint64_t totalTimeInStateMs __attribute__ ((aligned(8)));
    /**
     * Total number of times that the state was entered since the corresponding
     * PowerEntity was reset
     */
    uint64_t totalStateEntryCount __attribute__ ((aligned(8)));
    /**
     * Last time this state was entered. Time in milliseconds since the
     * corresponding PowerEntity was reset
     */
    uint64_t lastEntryTimestampMs __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData, powerEntityStateId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData, totalTimeInStateMs) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData, totalStateEntryCount) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData, lastEntryTimestampMs) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData) == 32, "wrong size");
static_assert(__alignof(::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData) == 8, "wrong alignment");

struct PowerEntityStateResidencyResult final {
    /**
     * Unique ID of the corresponding PowerEntity
     */
    uint32_t powerEntityId __attribute__ ((aligned(4)));
    /**
     * Residency data for each state the PowerEntity's state space
     */
    ::android::hardware::hidl_vec<::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData> stateResidencyData __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult, powerEntityId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult, stateResidencyData) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult) == 24, "wrong size");
static_assert(__alignof(::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::power::stats::V1_0::Status o);
static inline void PrintTo(::android::hardware::power::stats::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::power::stats::V1_0::Status lhs, const ::android::hardware::power::stats::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::power::stats::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::power::stats::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::power::stats::V1_0::Status lhs, const ::android::hardware::power::stats::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::power::stats::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::power::stats::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::power::stats::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::power::stats::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::power::stats::V1_0::RailInfo& o);
static inline void PrintTo(const ::android::hardware::power::stats::V1_0::RailInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::stats::V1_0::RailInfo& lhs, const ::android::hardware::power::stats::V1_0::RailInfo& rhs);
static inline bool operator!=(const ::android::hardware::power::stats::V1_0::RailInfo& lhs, const ::android::hardware::power::stats::V1_0::RailInfo& rhs);

static inline std::string toString(const ::android::hardware::power::stats::V1_0::EnergyData& o);
static inline void PrintTo(const ::android::hardware::power::stats::V1_0::EnergyData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::stats::V1_0::EnergyData& lhs, const ::android::hardware::power::stats::V1_0::EnergyData& rhs);
static inline bool operator!=(const ::android::hardware::power::stats::V1_0::EnergyData& lhs, const ::android::hardware::power::stats::V1_0::EnergyData& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::power::stats::V1_0::PowerEntityType o);
static inline void PrintTo(::android::hardware::power::stats::V1_0::PowerEntityType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::power::stats::V1_0::PowerEntityType lhs, const ::android::hardware::power::stats::V1_0::PowerEntityType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::power::stats::V1_0::PowerEntityType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::power::stats::V1_0::PowerEntityType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::power::stats::V1_0::PowerEntityType lhs, const ::android::hardware::power::stats::V1_0::PowerEntityType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::power::stats::V1_0::PowerEntityType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::power::stats::V1_0::PowerEntityType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::power::stats::V1_0::PowerEntityType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::power::stats::V1_0::PowerEntityType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::power::stats::V1_0::PowerEntityInfo& o);
static inline void PrintTo(const ::android::hardware::power::stats::V1_0::PowerEntityInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::stats::V1_0::PowerEntityInfo& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityInfo& rhs);
static inline bool operator!=(const ::android::hardware::power::stats::V1_0::PowerEntityInfo& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityInfo& rhs);

static inline std::string toString(const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& o);
static inline void PrintTo(const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& rhs);
static inline bool operator!=(const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& rhs);

static inline std::string toString(const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& o);
static inline void PrintTo(const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& rhs);
static inline bool operator!=(const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& rhs);

static inline std::string toString(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& o);
static inline void PrintTo(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& rhs);
static inline bool operator!=(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& rhs);

static inline std::string toString(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& o);
static inline void PrintTo(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& rhs);
static inline bool operator!=(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::power::stats::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::power::stats::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::power::stats::V1_0::Status::SUCCESS) == static_cast<uint32_t>(::android::hardware::power::stats::V1_0::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::power::stats::V1_0::Status::SUCCESS;
    }
    if ((o & ::android::hardware::power::stats::V1_0::Status::NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::power::stats::V1_0::Status::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::power::stats::V1_0::Status::NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::power::stats::V1_0::Status::INVALID_INPUT) == static_cast<uint32_t>(::android::hardware::power::stats::V1_0::Status::INVALID_INPUT)) {
        os += (first ? "" : " | ");
        os += "INVALID_INPUT";
        first = false;
        flipped |= ::android::hardware::power::stats::V1_0::Status::INVALID_INPUT;
    }
    if ((o & ::android::hardware::power::stats::V1_0::Status::FILESYSTEM_ERROR) == static_cast<uint32_t>(::android::hardware::power::stats::V1_0::Status::FILESYSTEM_ERROR)) {
        os += (first ? "" : " | ");
        os += "FILESYSTEM_ERROR";
        first = false;
        flipped |= ::android::hardware::power::stats::V1_0::Status::FILESYSTEM_ERROR;
    }
    if ((o & ::android::hardware::power::stats::V1_0::Status::INSUFFICIENT_RESOURCES) == static_cast<uint32_t>(::android::hardware::power::stats::V1_0::Status::INSUFFICIENT_RESOURCES)) {
        os += (first ? "" : " | ");
        os += "INSUFFICIENT_RESOURCES";
        first = false;
        flipped |= ::android::hardware::power::stats::V1_0::Status::INSUFFICIENT_RESOURCES;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::power::stats::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::power::stats::V1_0::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::power::stats::V1_0::Status::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    if (o == ::android::hardware::power::stats::V1_0::Status::INVALID_INPUT) {
        return "INVALID_INPUT";
    }
    if (o == ::android::hardware::power::stats::V1_0::Status::FILESYSTEM_ERROR) {
        return "FILESYSTEM_ERROR";
    }
    if (o == ::android::hardware::power::stats::V1_0::Status::INSUFFICIENT_RESOURCES) {
        return "INSUFFICIENT_RESOURCES";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::power::stats::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::power::stats::V1_0::RailInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".index = ";
    os += ::android::hardware::toString(o.index);
    os += ", .railName = ";
    os += ::android::hardware::toString(o.railName);
    os += ", .subsysName = ";
    os += ::android::hardware::toString(o.subsysName);
    os += ", .samplingRate = ";
    os += ::android::hardware::toString(o.samplingRate);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::stats::V1_0::RailInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::stats::V1_0::RailInfo& lhs, const ::android::hardware::power::stats::V1_0::RailInfo& rhs) {
    if (lhs.index != rhs.index) {
        return false;
    }
    if (lhs.railName != rhs.railName) {
        return false;
    }
    if (lhs.subsysName != rhs.subsysName) {
        return false;
    }
    if (lhs.samplingRate != rhs.samplingRate) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::stats::V1_0::RailInfo& lhs, const ::android::hardware::power::stats::V1_0::RailInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::power::stats::V1_0::EnergyData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".index = ";
    os += ::android::hardware::toString(o.index);
    os += ", .timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += ", .energy = ";
    os += ::android::hardware::toString(o.energy);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::stats::V1_0::EnergyData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::stats::V1_0::EnergyData& lhs, const ::android::hardware::power::stats::V1_0::EnergyData& rhs) {
    if (lhs.index != rhs.index) {
        return false;
    }
    if (lhs.timestamp != rhs.timestamp) {
        return false;
    }
    if (lhs.energy != rhs.energy) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::stats::V1_0::EnergyData& lhs, const ::android::hardware::power::stats::V1_0::EnergyData& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::power::stats::V1_0::PowerEntityType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::power::stats::V1_0::PowerEntityType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::power::stats::V1_0::PowerEntityType::SUBSYSTEM) == static_cast<uint32_t>(::android::hardware::power::stats::V1_0::PowerEntityType::SUBSYSTEM)) {
        os += (first ? "" : " | ");
        os += "SUBSYSTEM";
        first = false;
        flipped |= ::android::hardware::power::stats::V1_0::PowerEntityType::SUBSYSTEM;
    }
    if ((o & ::android::hardware::power::stats::V1_0::PowerEntityType::PERIPHERAL) == static_cast<uint32_t>(::android::hardware::power::stats::V1_0::PowerEntityType::PERIPHERAL)) {
        os += (first ? "" : " | ");
        os += "PERIPHERAL";
        first = false;
        flipped |= ::android::hardware::power::stats::V1_0::PowerEntityType::PERIPHERAL;
    }
    if ((o & ::android::hardware::power::stats::V1_0::PowerEntityType::POWER_DOMAIN) == static_cast<uint32_t>(::android::hardware::power::stats::V1_0::PowerEntityType::POWER_DOMAIN)) {
        os += (first ? "" : " | ");
        os += "POWER_DOMAIN";
        first = false;
        flipped |= ::android::hardware::power::stats::V1_0::PowerEntityType::POWER_DOMAIN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::power::stats::V1_0::PowerEntityType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::power::stats::V1_0::PowerEntityType::SUBSYSTEM) {
        return "SUBSYSTEM";
    }
    if (o == ::android::hardware::power::stats::V1_0::PowerEntityType::PERIPHERAL) {
        return "PERIPHERAL";
    }
    if (o == ::android::hardware::power::stats::V1_0::PowerEntityType::POWER_DOMAIN) {
        return "POWER_DOMAIN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::power::stats::V1_0::PowerEntityType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::power::stats::V1_0::PowerEntityInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".powerEntityId = ";
    os += ::android::hardware::toString(o.powerEntityId);
    os += ", .powerEntityName = ";
    os += ::android::hardware::toString(o.powerEntityName);
    os += ", .type = ";
    os += ::android::hardware::power::stats::V1_0::toString(o.type);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::stats::V1_0::PowerEntityInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::stats::V1_0::PowerEntityInfo& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityInfo& rhs) {
    if (lhs.powerEntityId != rhs.powerEntityId) {
        return false;
    }
    if (lhs.powerEntityName != rhs.powerEntityName) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::stats::V1_0::PowerEntityInfo& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".powerEntityStateId = ";
    os += ::android::hardware::toString(o.powerEntityStateId);
    os += ", .powerEntityStateName = ";
    os += ::android::hardware::toString(o.powerEntityStateName);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& rhs) {
    if (lhs.powerEntityStateId != rhs.powerEntityStateId) {
        return false;
    }
    if (lhs.powerEntityStateName != rhs.powerEntityStateName) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".powerEntityId = ";
    os += ::android::hardware::toString(o.powerEntityId);
    os += ", .states = ";
    os += ::android::hardware::toString(o.states);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& rhs) {
    if (lhs.powerEntityId != rhs.powerEntityId) {
        return false;
    }
    if (lhs.states != rhs.states) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateSpace& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".powerEntityStateId = ";
    os += ::android::hardware::toString(o.powerEntityStateId);
    os += ", .totalTimeInStateMs = ";
    os += ::android::hardware::toString(o.totalTimeInStateMs);
    os += ", .totalStateEntryCount = ";
    os += ::android::hardware::toString(o.totalStateEntryCount);
    os += ", .lastEntryTimestampMs = ";
    os += ::android::hardware::toString(o.lastEntryTimestampMs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& rhs) {
    if (lhs.powerEntityStateId != rhs.powerEntityStateId) {
        return false;
    }
    if (lhs.totalTimeInStateMs != rhs.totalTimeInStateMs) {
        return false;
    }
    if (lhs.totalStateEntryCount != rhs.totalStateEntryCount) {
        return false;
    }
    if (lhs.lastEntryTimestampMs != rhs.lastEntryTimestampMs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".powerEntityId = ";
    os += ::android::hardware::toString(o.powerEntityId);
    os += ", .stateResidencyData = ";
    os += ::android::hardware::toString(o.stateResidencyData);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& rhs) {
    if (lhs.powerEntityId != rhs.powerEntityId) {
        return false;
    }
    if (lhs.stateResidencyData != rhs.stateResidencyData) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& lhs, const ::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace stats
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
template<> inline constexpr std::array<::android::hardware::power::stats::V1_0::Status, 5> hidl_enum_values<::android::hardware::power::stats::V1_0::Status> = {
    ::android::hardware::power::stats::V1_0::Status::SUCCESS,
    ::android::hardware::power::stats::V1_0::Status::NOT_SUPPORTED,
    ::android::hardware::power::stats::V1_0::Status::INVALID_INPUT,
    ::android::hardware::power::stats::V1_0::Status::FILESYSTEM_ERROR,
    ::android::hardware::power::stats::V1_0::Status::INSUFFICIENT_RESOURCES,
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
template<> inline constexpr std::array<::android::hardware::power::stats::V1_0::PowerEntityType, 3> hidl_enum_values<::android::hardware::power::stats::V1_0::PowerEntityType> = {
    ::android::hardware::power::stats::V1_0::PowerEntityType::SUBSYSTEM,
    ::android::hardware::power::stats::V1_0::PowerEntityType::PERIPHERAL,
    ::android::hardware::power::stats::V1_0::PowerEntityType::POWER_DOMAIN,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_POWER_STATS_V1_0_TYPES_H
