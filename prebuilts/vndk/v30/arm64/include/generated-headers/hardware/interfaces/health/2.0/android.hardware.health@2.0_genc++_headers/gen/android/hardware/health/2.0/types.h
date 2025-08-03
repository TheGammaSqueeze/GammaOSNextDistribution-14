#ifndef HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_V2_0_TYPES_H

#include <android/hardware/health/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace health {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class Result : int32_t;
struct StorageAttribute;
struct StorageInfo;
struct DiskStats;
struct HealthInfo;

/**
 * Status values for HAL methods.
 */
enum class Result : int32_t {
    SUCCESS = 0,
    NOT_SUPPORTED = 1 /* ::android::hardware::health::V1_0::Result.SUCCESS implicitly + 1 */,
    UNKNOWN = 2 /* ::android::hardware::health::V1_0::Result.NOT_SUPPORTED implicitly + 1 */,
    NOT_FOUND = 3 /* ::android::hardware::health::V1_0::Result.UNKNOWN implicitly + 1 */,
    CALLBACK_DIED = 4 /* ::android::hardware::health::V2_0::Result.NOT_FOUND implicitly + 1 */,
};

/*
 * Identification attributes for a storage device.
 */
struct StorageAttribute final {
    /**
     * Set to true if internal storage
     */
    bool isInternal __attribute__ ((aligned(1)));
    /**
     * Set to true if this is the boot device.
     */
    bool isBootDevice __attribute__ ((aligned(1)));
    /**
     * Name of the storage device.
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::health::V2_0::StorageAttribute, isInternal) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::StorageAttribute, isBootDevice) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::StorageAttribute, name) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::health::V2_0::StorageAttribute) == 24, "wrong size");
static_assert(__alignof(::android::hardware::health::V2_0::StorageAttribute) == 8, "wrong alignment");

/*
 * Information on storage device including life time estimates, end of life
 * information and other attributes.
 */
struct StorageInfo final {
    /**
     * Attributes of the storage device whose info is contained by the struct.
     */
    ::android::hardware::health::V2_0::StorageAttribute attr __attribute__ ((aligned(8)));
    /**
     * pre-eol (end of life) information. Follows JEDEC standard No.84-B50.
     */
    uint16_t eol __attribute__ ((aligned(2)));
    /**
     * device life time estimation (type A). Follows JEDEC standard No.84-B50.
     */
    uint16_t lifetimeA __attribute__ ((aligned(2)));
    /**
     * device life time estimation (type B). Follows JEDEC standard No.84-B50.
     */
    uint16_t lifetimeB __attribute__ ((aligned(2)));
    /**
     * version string
     */
    ::android::hardware::hidl_string version __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::health::V2_0::StorageInfo, attr) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::StorageInfo, eol) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::StorageInfo, lifetimeA) == 26, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::StorageInfo, lifetimeB) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::StorageInfo, version) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::health::V2_0::StorageInfo) == 48, "wrong size");
static_assert(__alignof(::android::hardware::health::V2_0::StorageInfo) == 8, "wrong alignment");

/*
 * Disk statistics since boot.
 */
struct DiskStats final {
    /**
     * Number of reads processed.
     */
    uint64_t reads __attribute__ ((aligned(8)));
    /**
     * number of read I/Os merged with in-queue I/Os.
     */
    uint64_t readMerges __attribute__ ((aligned(8)));
    /**
     * number of sectors read.
     */
    uint64_t readSectors __attribute__ ((aligned(8)));
    /**
     * total wait time for read requests.
     */
    uint64_t readTicks __attribute__ ((aligned(8)));
    /**
     * number of writes processed.
     */
    uint64_t writes __attribute__ ((aligned(8)));
    /**
     * number of writes merged with in-queue I/Os.
     */
    uint64_t writeMerges __attribute__ ((aligned(8)));
    /**
     * number of sectors written.
     */
    uint64_t writeSectors __attribute__ ((aligned(8)));
    /**
     * total wait time for write requests.
     */
    uint64_t writeTicks __attribute__ ((aligned(8)));
    /**
     * number of I/Os currently in flight.
     */
    uint64_t ioInFlight __attribute__ ((aligned(8)));
    /**
     * total time this block device has been active.
     */
    uint64_t ioTicks __attribute__ ((aligned(8)));
    /**
     * total wait time for all requests.
     */
    uint64_t ioInQueue __attribute__ ((aligned(8)));
    /**
     * Attributes of the memory device.
     */
    ::android::hardware::health::V2_0::StorageAttribute attr __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, reads) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, readMerges) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, readSectors) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, readTicks) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, writes) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, writeMerges) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, writeSectors) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, writeTicks) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, ioInFlight) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, ioTicks) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, ioInQueue) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::DiskStats, attr) == 88, "wrong offset");
static_assert(sizeof(::android::hardware::health::V2_0::DiskStats) == 112, "wrong size");
static_assert(__alignof(::android::hardware::health::V2_0::DiskStats) == 8, "wrong alignment");

/**
 * Combined Health Information.
 */
struct HealthInfo final {
    /**
     * V1.0 HealthInfo.
     * If a member is unsupported, it is filled with:
     * - 0 (for integers);
     * - false (for booleans);
     * - empty string (for strings);
     * - UNKNOWN (for BatteryStatus and BatteryHealth).
     */
    ::android::hardware::health::V1_0::HealthInfo legacy __attribute__ ((aligned(8)));
    /**
     * Average battery current in uA. Will be 0 if unsupported.
     */
    int32_t batteryCurrentAverage __attribute__ ((aligned(4)));
    /**
     * Disk Statistics. Will be an empty vector if unsupported.
     */
    ::android::hardware::hidl_vec<::android::hardware::health::V2_0::DiskStats> diskStats __attribute__ ((aligned(8)));
    /**
     * Information on storage devices. Will be an empty vector if
     * unsupported.
     */
    ::android::hardware::hidl_vec<::android::hardware::health::V2_0::StorageInfo> storageInfos __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::health::V2_0::HealthInfo, legacy) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::HealthInfo, batteryCurrentAverage) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::HealthInfo, diskStats) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::health::V2_0::HealthInfo, storageInfos) == 96, "wrong offset");
static_assert(sizeof(::android::hardware::health::V2_0::HealthInfo) == 112, "wrong size");
static_assert(__alignof(::android::hardware::health::V2_0::HealthInfo) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::health::V2_0::Result o);
static inline void PrintTo(::android::hardware::health::V2_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::health::V2_0::Result lhs, const ::android::hardware::health::V2_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::health::V2_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::health::V2_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::health::V2_0::Result lhs, const ::android::hardware::health::V2_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::health::V2_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::health::V2_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::health::V2_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::health::V2_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::health::V2_0::StorageAttribute& o);
static inline void PrintTo(const ::android::hardware::health::V2_0::StorageAttribute& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::health::V2_0::StorageAttribute& lhs, const ::android::hardware::health::V2_0::StorageAttribute& rhs);
static inline bool operator!=(const ::android::hardware::health::V2_0::StorageAttribute& lhs, const ::android::hardware::health::V2_0::StorageAttribute& rhs);

static inline std::string toString(const ::android::hardware::health::V2_0::StorageInfo& o);
static inline void PrintTo(const ::android::hardware::health::V2_0::StorageInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::health::V2_0::StorageInfo& lhs, const ::android::hardware::health::V2_0::StorageInfo& rhs);
static inline bool operator!=(const ::android::hardware::health::V2_0::StorageInfo& lhs, const ::android::hardware::health::V2_0::StorageInfo& rhs);

static inline std::string toString(const ::android::hardware::health::V2_0::DiskStats& o);
static inline void PrintTo(const ::android::hardware::health::V2_0::DiskStats& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::health::V2_0::DiskStats& lhs, const ::android::hardware::health::V2_0::DiskStats& rhs);
static inline bool operator!=(const ::android::hardware::health::V2_0::DiskStats& lhs, const ::android::hardware::health::V2_0::DiskStats& rhs);

static inline std::string toString(const ::android::hardware::health::V2_0::HealthInfo& o);
static inline void PrintTo(const ::android::hardware::health::V2_0::HealthInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::health::V2_0::HealthInfo& lhs, const ::android::hardware::health::V2_0::HealthInfo& rhs);
static inline bool operator!=(const ::android::hardware::health::V2_0::HealthInfo& lhs, const ::android::hardware::health::V2_0::HealthInfo& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::health::V2_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::health::V2_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::health::V2_0::Result::SUCCESS) == static_cast<int32_t>(::android::hardware::health::V2_0::Result::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::health::V2_0::Result::SUCCESS;
    }
    if ((o & ::android::hardware::health::V2_0::Result::NOT_SUPPORTED) == static_cast<int32_t>(::android::hardware::health::V2_0::Result::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::health::V2_0::Result::NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::health::V2_0::Result::UNKNOWN) == static_cast<int32_t>(::android::hardware::health::V2_0::Result::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::health::V2_0::Result::UNKNOWN;
    }
    if ((o & ::android::hardware::health::V2_0::Result::NOT_FOUND) == static_cast<int32_t>(::android::hardware::health::V2_0::Result::NOT_FOUND)) {
        os += (first ? "" : " | ");
        os += "NOT_FOUND";
        first = false;
        flipped |= ::android::hardware::health::V2_0::Result::NOT_FOUND;
    }
    if ((o & ::android::hardware::health::V2_0::Result::CALLBACK_DIED) == static_cast<int32_t>(::android::hardware::health::V2_0::Result::CALLBACK_DIED)) {
        os += (first ? "" : " | ");
        os += "CALLBACK_DIED";
        first = false;
        flipped |= ::android::hardware::health::V2_0::Result::CALLBACK_DIED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::health::V2_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::health::V2_0::Result::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::health::V2_0::Result::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    if (o == ::android::hardware::health::V2_0::Result::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::health::V2_0::Result::NOT_FOUND) {
        return "NOT_FOUND";
    }
    if (o == ::android::hardware::health::V2_0::Result::CALLBACK_DIED) {
        return "CALLBACK_DIED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::health::V2_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::health::V2_0::StorageAttribute& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".isInternal = ";
    os += ::android::hardware::toString(o.isInternal);
    os += ", .isBootDevice = ";
    os += ::android::hardware::toString(o.isBootDevice);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::health::V2_0::StorageAttribute& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::health::V2_0::StorageAttribute& lhs, const ::android::hardware::health::V2_0::StorageAttribute& rhs) {
    if (lhs.isInternal != rhs.isInternal) {
        return false;
    }
    if (lhs.isBootDevice != rhs.isBootDevice) {
        return false;
    }
    if (lhs.name != rhs.name) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::health::V2_0::StorageAttribute& lhs, const ::android::hardware::health::V2_0::StorageAttribute& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::health::V2_0::StorageInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".attr = ";
    os += ::android::hardware::health::V2_0::toString(o.attr);
    os += ", .eol = ";
    os += ::android::hardware::toString(o.eol);
    os += ", .lifetimeA = ";
    os += ::android::hardware::toString(o.lifetimeA);
    os += ", .lifetimeB = ";
    os += ::android::hardware::toString(o.lifetimeB);
    os += ", .version = ";
    os += ::android::hardware::toString(o.version);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::health::V2_0::StorageInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::health::V2_0::StorageInfo& lhs, const ::android::hardware::health::V2_0::StorageInfo& rhs) {
    if (lhs.attr != rhs.attr) {
        return false;
    }
    if (lhs.eol != rhs.eol) {
        return false;
    }
    if (lhs.lifetimeA != rhs.lifetimeA) {
        return false;
    }
    if (lhs.lifetimeB != rhs.lifetimeB) {
        return false;
    }
    if (lhs.version != rhs.version) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::health::V2_0::StorageInfo& lhs, const ::android::hardware::health::V2_0::StorageInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::health::V2_0::DiskStats& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".reads = ";
    os += ::android::hardware::toString(o.reads);
    os += ", .readMerges = ";
    os += ::android::hardware::toString(o.readMerges);
    os += ", .readSectors = ";
    os += ::android::hardware::toString(o.readSectors);
    os += ", .readTicks = ";
    os += ::android::hardware::toString(o.readTicks);
    os += ", .writes = ";
    os += ::android::hardware::toString(o.writes);
    os += ", .writeMerges = ";
    os += ::android::hardware::toString(o.writeMerges);
    os += ", .writeSectors = ";
    os += ::android::hardware::toString(o.writeSectors);
    os += ", .writeTicks = ";
    os += ::android::hardware::toString(o.writeTicks);
    os += ", .ioInFlight = ";
    os += ::android::hardware::toString(o.ioInFlight);
    os += ", .ioTicks = ";
    os += ::android::hardware::toString(o.ioTicks);
    os += ", .ioInQueue = ";
    os += ::android::hardware::toString(o.ioInQueue);
    os += ", .attr = ";
    os += ::android::hardware::health::V2_0::toString(o.attr);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::health::V2_0::DiskStats& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::health::V2_0::DiskStats& lhs, const ::android::hardware::health::V2_0::DiskStats& rhs) {
    if (lhs.reads != rhs.reads) {
        return false;
    }
    if (lhs.readMerges != rhs.readMerges) {
        return false;
    }
    if (lhs.readSectors != rhs.readSectors) {
        return false;
    }
    if (lhs.readTicks != rhs.readTicks) {
        return false;
    }
    if (lhs.writes != rhs.writes) {
        return false;
    }
    if (lhs.writeMerges != rhs.writeMerges) {
        return false;
    }
    if (lhs.writeSectors != rhs.writeSectors) {
        return false;
    }
    if (lhs.writeTicks != rhs.writeTicks) {
        return false;
    }
    if (lhs.ioInFlight != rhs.ioInFlight) {
        return false;
    }
    if (lhs.ioTicks != rhs.ioTicks) {
        return false;
    }
    if (lhs.ioInQueue != rhs.ioInQueue) {
        return false;
    }
    if (lhs.attr != rhs.attr) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::health::V2_0::DiskStats& lhs, const ::android::hardware::health::V2_0::DiskStats& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::health::V2_0::HealthInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".legacy = ";
    os += ::android::hardware::health::V1_0::toString(o.legacy);
    os += ", .batteryCurrentAverage = ";
    os += ::android::hardware::toString(o.batteryCurrentAverage);
    os += ", .diskStats = ";
    os += ::android::hardware::toString(o.diskStats);
    os += ", .storageInfos = ";
    os += ::android::hardware::toString(o.storageInfos);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::health::V2_0::HealthInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::health::V2_0::HealthInfo& lhs, const ::android::hardware::health::V2_0::HealthInfo& rhs) {
    if (lhs.legacy != rhs.legacy) {
        return false;
    }
    if (lhs.batteryCurrentAverage != rhs.batteryCurrentAverage) {
        return false;
    }
    if (lhs.diskStats != rhs.diskStats) {
        return false;
    }
    if (lhs.storageInfos != rhs.storageInfos) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::health::V2_0::HealthInfo& lhs, const ::android::hardware::health::V2_0::HealthInfo& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_0
}  // namespace health
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
template<> inline constexpr std::array<::android::hardware::health::V2_0::Result, 5> hidl_enum_values<::android::hardware::health::V2_0::Result> = {
    ::android::hardware::health::V2_0::Result::SUCCESS,
    ::android::hardware::health::V2_0::Result::NOT_SUPPORTED,
    ::android::hardware::health::V2_0::Result::UNKNOWN,
    ::android::hardware::health::V2_0::Result::NOT_FOUND,
    ::android::hardware::health::V2_0::Result::CALLBACK_DIED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_V2_0_TYPES_H
