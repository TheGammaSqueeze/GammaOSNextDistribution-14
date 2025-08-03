#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class GnssMax : uint32_t;
enum class GnssConstellationType : uint8_t;
enum class GnssLocationFlags : uint16_t;
struct GnssLocation;

enum class GnssMax : uint32_t {
    /**
     * Maximum number of SVs for gnssSvStatusCb().
     */
    SVS_COUNT = 64u,
};

/**
 * Milliseconds since January 1, 1970
 */
typedef int64_t GnssUtcTime;

/**
 * Constellation type of GnssSvInfo
 */
enum class GnssConstellationType : uint8_t {
    UNKNOWN = 0,
    GPS = 1,
    SBAS = 2,
    GLONASS = 3,
    QZSS = 4,
    BEIDOU = 5,
    GALILEO = 6,
};

/**
 * Bit mask to indicate which values are valid in a GnssLocation object.
 */
enum class GnssLocationFlags : uint16_t {
    /**
     * GnssLocation has valid latitude and longitude.
     */
    HAS_LAT_LONG = 1 /* 0x0001 */,
    /**
     * GnssLocation has valid altitude.
     */
    HAS_ALTITUDE = 2 /* 0x0002 */,
    /**
     * GnssLocation has valid speed.
     */
    HAS_SPEED = 4 /* 0x0004 */,
    /**
     * GnssLocation has valid bearing.
     */
    HAS_BEARING = 8 /* 0x0008 */,
    /**
     * GpsLocation has valid horizontal accuracy.
     */
    HAS_HORIZONTAL_ACCURACY = 16 /* 0x0010 */,
    /**
     * GpsLocation has valid vertical accuracy.
     */
    HAS_VERTICAL_ACCURACY = 32 /* 0x0020 */,
    /**
     * GpsLocation has valid speed accuracy.
     */
    HAS_SPEED_ACCURACY = 64 /* 0x0040 */,
    /**
     * GpsLocation has valid bearing accuracy.
     */
    HAS_BEARING_ACCURACY = 128 /* 0x0080 */,
};

/**
 * Represents a location.
 */
struct GnssLocation final {
    /**
     * Contains GnssLocationFlags bits.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::GnssLocationFlags> gnssLocationFlags __attribute__ ((aligned(2)));
    /**
     * Represents latitude in degrees.
     */
    double latitudeDegrees __attribute__ ((aligned(8)));
    /**
     * Represents longitude in degrees.
     */
    double longitudeDegrees __attribute__ ((aligned(8)));
    /**
     * Represents altitude in meters above the WGS 84 reference ellipsoid.
     */
    double altitudeMeters __attribute__ ((aligned(8)));
    /**
     * Represents speed in meters per second.
     */
    float speedMetersPerSec __attribute__ ((aligned(4)));
    /**
     * Represents heading in degrees.
     */
    float bearingDegrees __attribute__ ((aligned(4)));
    /**
     * Represents expected horizontal position accuracy, radial, in meters
     * (68% confidence).
     */
    float horizontalAccuracyMeters __attribute__ ((aligned(4)));
    /**
     * Represents expected vertical position accuracy in meters
     * (68% confidence).
     */
    float verticalAccuracyMeters __attribute__ ((aligned(4)));
    /**
     * Represents expected speed accuracy in meter per seconds
     * (68% confidence).
     */
    float speedAccuracyMetersPerSecond __attribute__ ((aligned(4)));
    /**
     * Represents expected bearing accuracy in degrees
     * (68% confidence).
     */
    float bearingAccuracyDegrees __attribute__ ((aligned(4)));
    /**
     * Timestamp for the location fix.
     */
    int64_t timestamp __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, gnssLocationFlags) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, latitudeDegrees) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, longitudeDegrees) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, altitudeMeters) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, speedMetersPerSec) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, bearingDegrees) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, horizontalAccuracyMeters) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, verticalAccuracyMeters) == 44, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, speedAccuracyMetersPerSecond) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, bearingAccuracyDegrees) == 52, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V1_0::GnssLocation, timestamp) == 56, "wrong offset");
static_assert(sizeof(::android::hardware::gnss::V1_0::GnssLocation) == 64, "wrong size");
static_assert(__alignof(::android::hardware::gnss::V1_0::GnssLocation) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::GnssMax o);
static inline void PrintTo(::android::hardware::gnss::V1_0::GnssMax o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::gnss::V1_0::GnssMax lhs, const ::android::hardware::gnss::V1_0::GnssMax rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::gnss::V1_0::GnssMax rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::gnss::V1_0::GnssMax lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::gnss::V1_0::GnssMax lhs, const ::android::hardware::gnss::V1_0::GnssMax rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::gnss::V1_0::GnssMax rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::gnss::V1_0::GnssMax lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::gnss::V1_0::GnssMax e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::gnss::V1_0::GnssMax e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::GnssConstellationType o);
static inline void PrintTo(::android::hardware::gnss::V1_0::GnssConstellationType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::GnssConstellationType lhs, const ::android::hardware::gnss::V1_0::GnssConstellationType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::GnssConstellationType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::GnssConstellationType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::GnssConstellationType lhs, const ::android::hardware::gnss::V1_0::GnssConstellationType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::GnssConstellationType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::GnssConstellationType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::GnssConstellationType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::GnssConstellationType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::GnssLocationFlags o);
static inline void PrintTo(::android::hardware::gnss::V1_0::GnssLocationFlags o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::gnss::V1_0::GnssLocationFlags lhs, const ::android::hardware::gnss::V1_0::GnssLocationFlags rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::gnss::V1_0::GnssLocationFlags rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::gnss::V1_0::GnssLocationFlags lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::gnss::V1_0::GnssLocationFlags lhs, const ::android::hardware::gnss::V1_0::GnssLocationFlags rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::gnss::V1_0::GnssLocationFlags rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::gnss::V1_0::GnssLocationFlags lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::gnss::V1_0::GnssLocationFlags e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::gnss::V1_0::GnssLocationFlags e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::GnssLocation& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::GnssLocation& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::GnssLocation& lhs, const ::android::hardware::gnss::V1_0::GnssLocation& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::GnssLocation& lhs, const ::android::hardware::gnss::V1_0::GnssLocation& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V1_0::GnssMax>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::GnssMax> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::GnssMax::SVS_COUNT) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::GnssMax::SVS_COUNT)) {
        os += (first ? "" : " | ");
        os += "SVS_COUNT";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssMax::SVS_COUNT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::GnssMax o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::GnssMax::SVS_COUNT) {
        return "SVS_COUNT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::GnssMax o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::GnssConstellationType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::GnssConstellationType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::GnssConstellationType::UNKNOWN) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::GnssConstellationType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssConstellationType::UNKNOWN;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssConstellationType::GPS) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::GnssConstellationType::GPS)) {
        os += (first ? "" : " | ");
        os += "GPS";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssConstellationType::GPS;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssConstellationType::SBAS) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::GnssConstellationType::SBAS)) {
        os += (first ? "" : " | ");
        os += "SBAS";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssConstellationType::SBAS;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssConstellationType::GLONASS) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::GnssConstellationType::GLONASS)) {
        os += (first ? "" : " | ");
        os += "GLONASS";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssConstellationType::GLONASS;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssConstellationType::QZSS) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::GnssConstellationType::QZSS)) {
        os += (first ? "" : " | ");
        os += "QZSS";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssConstellationType::QZSS;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssConstellationType::BEIDOU) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::GnssConstellationType::BEIDOU)) {
        os += (first ? "" : " | ");
        os += "BEIDOU";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssConstellationType::BEIDOU;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssConstellationType::GALILEO) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::GnssConstellationType::GALILEO)) {
        os += (first ? "" : " | ");
        os += "GALILEO";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssConstellationType::GALILEO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::GnssConstellationType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::GnssConstellationType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssConstellationType::GPS) {
        return "GPS";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssConstellationType::SBAS) {
        return "SBAS";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssConstellationType::GLONASS) {
        return "GLONASS";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssConstellationType::QZSS) {
        return "QZSS";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssConstellationType::BEIDOU) {
        return "BEIDOU";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssConstellationType::GALILEO) {
        return "GALILEO";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::GnssConstellationType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::GnssLocationFlags>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::GnssLocationFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_LAT_LONG) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_LAT_LONG)) {
        os += (first ? "" : " | ");
        os += "HAS_LAT_LONG";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_LAT_LONG;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_ALTITUDE) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_ALTITUDE)) {
        os += (first ? "" : " | ");
        os += "HAS_ALTITUDE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_ALTITUDE;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_SPEED) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_SPEED)) {
        os += (first ? "" : " | ");
        os += "HAS_SPEED";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_SPEED;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_BEARING) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_BEARING)) {
        os += (first ? "" : " | ");
        os += "HAS_BEARING";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_BEARING;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_HORIZONTAL_ACCURACY) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_HORIZONTAL_ACCURACY)) {
        os += (first ? "" : " | ");
        os += "HAS_HORIZONTAL_ACCURACY";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_HORIZONTAL_ACCURACY;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_VERTICAL_ACCURACY) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_VERTICAL_ACCURACY)) {
        os += (first ? "" : " | ");
        os += "HAS_VERTICAL_ACCURACY";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_VERTICAL_ACCURACY;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_SPEED_ACCURACY) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_SPEED_ACCURACY)) {
        os += (first ? "" : " | ");
        os += "HAS_SPEED_ACCURACY";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_SPEED_ACCURACY;
    }
    if ((o & ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_BEARING_ACCURACY) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_BEARING_ACCURACY)) {
        os += (first ? "" : " | ");
        os += "HAS_BEARING_ACCURACY";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_BEARING_ACCURACY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::GnssLocationFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_LAT_LONG) {
        return "HAS_LAT_LONG";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_ALTITUDE) {
        return "HAS_ALTITUDE";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_SPEED) {
        return "HAS_SPEED";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_BEARING) {
        return "HAS_BEARING";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_HORIZONTAL_ACCURACY) {
        return "HAS_HORIZONTAL_ACCURACY";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_VERTICAL_ACCURACY) {
        return "HAS_VERTICAL_ACCURACY";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_SPEED_ACCURACY) {
        return "HAS_SPEED_ACCURACY";
    }
    if (o == ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_BEARING_ACCURACY) {
        return "HAS_BEARING_ACCURACY";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::GnssLocationFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::GnssLocation& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".gnssLocationFlags = ";
    os += ::android::hardware::gnss::V1_0::toString<::android::hardware::gnss::V1_0::GnssLocationFlags>(o.gnssLocationFlags);
    os += ", .latitudeDegrees = ";
    os += ::android::hardware::toString(o.latitudeDegrees);
    os += ", .longitudeDegrees = ";
    os += ::android::hardware::toString(o.longitudeDegrees);
    os += ", .altitudeMeters = ";
    os += ::android::hardware::toString(o.altitudeMeters);
    os += ", .speedMetersPerSec = ";
    os += ::android::hardware::toString(o.speedMetersPerSec);
    os += ", .bearingDegrees = ";
    os += ::android::hardware::toString(o.bearingDegrees);
    os += ", .horizontalAccuracyMeters = ";
    os += ::android::hardware::toString(o.horizontalAccuracyMeters);
    os += ", .verticalAccuracyMeters = ";
    os += ::android::hardware::toString(o.verticalAccuracyMeters);
    os += ", .speedAccuracyMetersPerSecond = ";
    os += ::android::hardware::toString(o.speedAccuracyMetersPerSecond);
    os += ", .bearingAccuracyDegrees = ";
    os += ::android::hardware::toString(o.bearingAccuracyDegrees);
    os += ", .timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::GnssLocation& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::GnssLocation& lhs, const ::android::hardware::gnss::V1_0::GnssLocation& rhs) {
    if (lhs.gnssLocationFlags != rhs.gnssLocationFlags) {
        return false;
    }
    if (lhs.latitudeDegrees != rhs.latitudeDegrees) {
        return false;
    }
    if (lhs.longitudeDegrees != rhs.longitudeDegrees) {
        return false;
    }
    if (lhs.altitudeMeters != rhs.altitudeMeters) {
        return false;
    }
    if (lhs.speedMetersPerSec != rhs.speedMetersPerSec) {
        return false;
    }
    if (lhs.bearingDegrees != rhs.bearingDegrees) {
        return false;
    }
    if (lhs.horizontalAccuracyMeters != rhs.horizontalAccuracyMeters) {
        return false;
    }
    if (lhs.verticalAccuracyMeters != rhs.verticalAccuracyMeters) {
        return false;
    }
    if (lhs.speedAccuracyMetersPerSecond != rhs.speedAccuracyMetersPerSecond) {
        return false;
    }
    if (lhs.bearingAccuracyDegrees != rhs.bearingAccuracyDegrees) {
        return false;
    }
    if (lhs.timestamp != rhs.timestamp) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::GnssLocation& lhs, const ::android::hardware::gnss::V1_0::GnssLocation& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::GnssMax, 1> hidl_enum_values<::android::hardware::gnss::V1_0::GnssMax> = {
    ::android::hardware::gnss::V1_0::GnssMax::SVS_COUNT,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::GnssConstellationType, 7> hidl_enum_values<::android::hardware::gnss::V1_0::GnssConstellationType> = {
    ::android::hardware::gnss::V1_0::GnssConstellationType::UNKNOWN,
    ::android::hardware::gnss::V1_0::GnssConstellationType::GPS,
    ::android::hardware::gnss::V1_0::GnssConstellationType::SBAS,
    ::android::hardware::gnss::V1_0::GnssConstellationType::GLONASS,
    ::android::hardware::gnss::V1_0::GnssConstellationType::QZSS,
    ::android::hardware::gnss::V1_0::GnssConstellationType::BEIDOU,
    ::android::hardware::gnss::V1_0::GnssConstellationType::GALILEO,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::GnssLocationFlags, 8> hidl_enum_values<::android::hardware::gnss::V1_0::GnssLocationFlags> = {
    ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_LAT_LONG,
    ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_ALTITUDE,
    ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_SPEED,
    ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_BEARING,
    ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_HORIZONTAL_ACCURACY,
    ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_VERTICAL_ACCURACY,
    ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_SPEED_ACCURACY,
    ::android::hardware::gnss::V1_0::GnssLocationFlags::HAS_BEARING_ACCURACY,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_TYPES_H
