#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_TYPES_H

#include <android/hardware/gnss/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class ElapsedRealtimeFlags : uint16_t;
struct ElapsedRealtime;
struct GnssLocation;
enum class GnssConstellationType : uint8_t;

/**
 * Network handle type.
 */
typedef uint64_t net_handle_t;

/**
 * Flags indicating the validity of the fields in ElapsedRealtime.
 */
enum class ElapsedRealtimeFlags : uint16_t {
    /**
     * A valid timestampNs is stored in the data structure.
     */
    HAS_TIMESTAMP_NS = 1 /* 1 << 0 */,
    /**
     * A valid timeUncertaintyNs is stored in the data structure.
     */
    HAS_TIME_UNCERTAINTY_NS = 2 /* 1 << 1 */,
};

/**
 * Represents an estimate of elapsed time since boot of Android for a given event.
 *
 * This timestamp MUST represent the time the event happened and MUST be synchronized
 * with the SystemClock.elapsedRealtimeNanos() clock.
 */
struct ElapsedRealtime final {
    /**
     * A set of flags indicating the validity of each field in this data structure.
     *
     * Fields may have invalid information in them, if not marked as valid by the
     * corresponding bit in flags.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_0::ElapsedRealtimeFlags> flags __attribute__ ((aligned(2)));
    /**
     * Estimate of the elapsed time since boot value for the corresponding event in nanoseconds.
     */
    uint64_t timestampNs __attribute__ ((aligned(8)));
    /**
     * Estimate of the relative precision of the alignment of this SystemClock
     * timestamp, with the reported measurements in nanoseconds (68% confidence).
     */
    uint64_t timeUncertaintyNs __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::gnss::V2_0::ElapsedRealtime, flags) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V2_0::ElapsedRealtime, timestampNs) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V2_0::ElapsedRealtime, timeUncertaintyNs) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::gnss::V2_0::ElapsedRealtime) == 24, "wrong size");
static_assert(__alignof(::android::hardware::gnss::V2_0::ElapsedRealtime) == 8, "wrong alignment");

/**
 * Represents a location.
 */
struct GnssLocation final {
    ::android::hardware::gnss::V1_0::GnssLocation v1_0 __attribute__ ((aligned(8)));
    /**
     * Timing information of the GNSS location synchronized with SystemClock.elapsedRealtimeNanos()
     * clock.
     *
     * This clock information can be obtained from SystemClock.elapsedRealtimeNanos(), when the GNSS
     * is attached straight to the AP/SOC. When it is attached to a separate module the timestamp
     * needs to be estimated by syncing the notion of time via PTP or some other mechanism.
     */
    ::android::hardware::gnss::V2_0::ElapsedRealtime elapsedRealtime __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::gnss::V2_0::GnssLocation, v1_0) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V2_0::GnssLocation, elapsedRealtime) == 64, "wrong offset");
static_assert(sizeof(::android::hardware::gnss::V2_0::GnssLocation) == 88, "wrong size");
static_assert(__alignof(::android::hardware::gnss::V2_0::GnssLocation) == 8, "wrong alignment");

/**
 * GNSS constellation type
 *
 * This is to specify the navigation satellite system, for example, as listed in Section 3.5 in
 * RINEX Version 3.04.
 */
enum class GnssConstellationType : uint8_t {
    UNKNOWN = 0,
    /**
     * Global Positioning System.
     */
    GPS = 1,
    /**
     * Satellite-Based Augmentation System.
     */
    SBAS = 2,
    /**
     * Global Navigation Satellite System.
     */
    GLONASS = 3,
    /**
     * Quasi-Zenith Satellite System.
     */
    QZSS = 4,
    /**
     * BeiDou Navigation Satellite System.
     */
    BEIDOU = 5,
    /**
     * Galileo Navigation Satellite System.
     */
    GALILEO = 6,
    /**
     * Indian Regional Navigation Satellite System.
     */
    IRNSS = 7,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::gnss::V2_0::ElapsedRealtimeFlags o);
static inline void PrintTo(::android::hardware::gnss::V2_0::ElapsedRealtimeFlags o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags lhs, const ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags lhs, const ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::V2_0::ElapsedRealtime& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_0::ElapsedRealtime& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_0::ElapsedRealtime& lhs, const ::android::hardware::gnss::V2_0::ElapsedRealtime& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_0::ElapsedRealtime& lhs, const ::android::hardware::gnss::V2_0::ElapsedRealtime& rhs);

static inline std::string toString(const ::android::hardware::gnss::V2_0::GnssLocation& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_0::GnssLocation& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_0::GnssLocation& lhs, const ::android::hardware::gnss::V2_0::GnssLocation& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_0::GnssLocation& lhs, const ::android::hardware::gnss::V2_0::GnssLocation& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V2_0::GnssConstellationType o);
static inline void PrintTo(::android::hardware::gnss::V2_0::GnssConstellationType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V2_0::GnssConstellationType lhs, const ::android::hardware::gnss::V2_0::GnssConstellationType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V2_0::GnssConstellationType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V2_0::GnssConstellationType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V2_0::GnssConstellationType lhs, const ::android::hardware::gnss::V2_0::GnssConstellationType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V2_0::GnssConstellationType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V2_0::GnssConstellationType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V2_0::GnssConstellationType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V2_0::GnssConstellationType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V2_0::ElapsedRealtimeFlags>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_0::ElapsedRealtimeFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags::HAS_TIMESTAMP_NS) == static_cast<uint16_t>(::android::hardware::gnss::V2_0::ElapsedRealtimeFlags::HAS_TIMESTAMP_NS)) {
        os += (first ? "" : " | ");
        os += "HAS_TIMESTAMP_NS";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags::HAS_TIMESTAMP_NS;
    }
    if ((o & ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags::HAS_TIME_UNCERTAINTY_NS) == static_cast<uint16_t>(::android::hardware::gnss::V2_0::ElapsedRealtimeFlags::HAS_TIME_UNCERTAINTY_NS)) {
        os += (first ? "" : " | ");
        os += "HAS_TIME_UNCERTAINTY_NS";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags::HAS_TIME_UNCERTAINTY_NS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V2_0::ElapsedRealtimeFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags::HAS_TIMESTAMP_NS) {
        return "HAS_TIMESTAMP_NS";
    }
    if (o == ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags::HAS_TIME_UNCERTAINTY_NS) {
        return "HAS_TIME_UNCERTAINTY_NS";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V2_0::ElapsedRealtimeFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::V2_0::ElapsedRealtime& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".flags = ";
    os += ::android::hardware::gnss::V2_0::toString<::android::hardware::gnss::V2_0::ElapsedRealtimeFlags>(o.flags);
    os += ", .timestampNs = ";
    os += ::android::hardware::toString(o.timestampNs);
    os += ", .timeUncertaintyNs = ";
    os += ::android::hardware::toString(o.timeUncertaintyNs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_0::ElapsedRealtime& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_0::ElapsedRealtime& lhs, const ::android::hardware::gnss::V2_0::ElapsedRealtime& rhs) {
    if (lhs.flags != rhs.flags) {
        return false;
    }
    if (lhs.timestampNs != rhs.timestampNs) {
        return false;
    }
    if (lhs.timeUncertaintyNs != rhs.timeUncertaintyNs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_0::ElapsedRealtime& lhs, const ::android::hardware::gnss::V2_0::ElapsedRealtime& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V2_0::GnssLocation& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v1_0 = ";
    os += ::android::hardware::gnss::V1_0::toString(o.v1_0);
    os += ", .elapsedRealtime = ";
    os += ::android::hardware::gnss::V2_0::toString(o.elapsedRealtime);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_0::GnssLocation& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_0::GnssLocation& lhs, const ::android::hardware::gnss::V2_0::GnssLocation& rhs) {
    if (lhs.v1_0 != rhs.v1_0) {
        return false;
    }
    if (lhs.elapsedRealtime != rhs.elapsedRealtime) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_0::GnssLocation& lhs, const ::android::hardware::gnss::V2_0::GnssLocation& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::gnss::V2_0::GnssConstellationType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_0::GnssConstellationType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V2_0::GnssConstellationType::UNKNOWN) == static_cast<uint8_t>(::android::hardware::gnss::V2_0::GnssConstellationType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::GnssConstellationType::UNKNOWN;
    }
    if ((o & ::android::hardware::gnss::V2_0::GnssConstellationType::GPS) == static_cast<uint8_t>(::android::hardware::gnss::V2_0::GnssConstellationType::GPS)) {
        os += (first ? "" : " | ");
        os += "GPS";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::GnssConstellationType::GPS;
    }
    if ((o & ::android::hardware::gnss::V2_0::GnssConstellationType::SBAS) == static_cast<uint8_t>(::android::hardware::gnss::V2_0::GnssConstellationType::SBAS)) {
        os += (first ? "" : " | ");
        os += "SBAS";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::GnssConstellationType::SBAS;
    }
    if ((o & ::android::hardware::gnss::V2_0::GnssConstellationType::GLONASS) == static_cast<uint8_t>(::android::hardware::gnss::V2_0::GnssConstellationType::GLONASS)) {
        os += (first ? "" : " | ");
        os += "GLONASS";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::GnssConstellationType::GLONASS;
    }
    if ((o & ::android::hardware::gnss::V2_0::GnssConstellationType::QZSS) == static_cast<uint8_t>(::android::hardware::gnss::V2_0::GnssConstellationType::QZSS)) {
        os += (first ? "" : " | ");
        os += "QZSS";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::GnssConstellationType::QZSS;
    }
    if ((o & ::android::hardware::gnss::V2_0::GnssConstellationType::BEIDOU) == static_cast<uint8_t>(::android::hardware::gnss::V2_0::GnssConstellationType::BEIDOU)) {
        os += (first ? "" : " | ");
        os += "BEIDOU";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::GnssConstellationType::BEIDOU;
    }
    if ((o & ::android::hardware::gnss::V2_0::GnssConstellationType::GALILEO) == static_cast<uint8_t>(::android::hardware::gnss::V2_0::GnssConstellationType::GALILEO)) {
        os += (first ? "" : " | ");
        os += "GALILEO";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::GnssConstellationType::GALILEO;
    }
    if ((o & ::android::hardware::gnss::V2_0::GnssConstellationType::IRNSS) == static_cast<uint8_t>(::android::hardware::gnss::V2_0::GnssConstellationType::IRNSS)) {
        os += (first ? "" : " | ");
        os += "IRNSS";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::GnssConstellationType::IRNSS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V2_0::GnssConstellationType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V2_0::GnssConstellationType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::gnss::V2_0::GnssConstellationType::GPS) {
        return "GPS";
    }
    if (o == ::android::hardware::gnss::V2_0::GnssConstellationType::SBAS) {
        return "SBAS";
    }
    if (o == ::android::hardware::gnss::V2_0::GnssConstellationType::GLONASS) {
        return "GLONASS";
    }
    if (o == ::android::hardware::gnss::V2_0::GnssConstellationType::QZSS) {
        return "QZSS";
    }
    if (o == ::android::hardware::gnss::V2_0::GnssConstellationType::BEIDOU) {
        return "BEIDOU";
    }
    if (o == ::android::hardware::gnss::V2_0::GnssConstellationType::GALILEO) {
        return "GALILEO";
    }
    if (o == ::android::hardware::gnss::V2_0::GnssConstellationType::IRNSS) {
        return "IRNSS";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V2_0::GnssConstellationType o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V2_0
}  // namespace gnss
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
template<> inline constexpr std::array<::android::hardware::gnss::V2_0::ElapsedRealtimeFlags, 2> hidl_enum_values<::android::hardware::gnss::V2_0::ElapsedRealtimeFlags> = {
    ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags::HAS_TIMESTAMP_NS,
    ::android::hardware::gnss::V2_0::ElapsedRealtimeFlags::HAS_TIME_UNCERTAINTY_NS,
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
template<> inline constexpr std::array<::android::hardware::gnss::V2_0::GnssConstellationType, 8> hidl_enum_values<::android::hardware::gnss::V2_0::GnssConstellationType> = {
    ::android::hardware::gnss::V2_0::GnssConstellationType::UNKNOWN,
    ::android::hardware::gnss::V2_0::GnssConstellationType::GPS,
    ::android::hardware::gnss::V2_0::GnssConstellationType::SBAS,
    ::android::hardware::gnss::V2_0::GnssConstellationType::GLONASS,
    ::android::hardware::gnss::V2_0::GnssConstellationType::QZSS,
    ::android::hardware::gnss::V2_0::GnssConstellationType::BEIDOU,
    ::android::hardware::gnss::V2_0::GnssConstellationType::GALILEO,
    ::android::hardware::gnss::V2_0::GnssConstellationType::IRNSS,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_TYPES_H
