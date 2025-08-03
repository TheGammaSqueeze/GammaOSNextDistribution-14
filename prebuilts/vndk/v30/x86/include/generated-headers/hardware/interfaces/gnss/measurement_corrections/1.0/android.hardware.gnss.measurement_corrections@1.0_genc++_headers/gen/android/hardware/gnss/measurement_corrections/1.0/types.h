#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_0_TYPES_H

#include <android/hardware/gnss/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class GnssSingleSatCorrectionFlags : uint16_t;
struct ReflectingPlane;
struct SingleSatCorrection;
struct MeasurementCorrections;

// Order of inner types was changed for forward reference support.

/**
 * Bit mask to indicate which values are valid in a SingleSatCorrection object.
 */
enum class GnssSingleSatCorrectionFlags : uint16_t {
    /**
     * GnssSingleSatCorrectionFlags has valid satellite-is-line-of-sight-probability field.
     */
    HAS_SAT_IS_LOS_PROBABILITY = 1 /* 0x0001 */,
    /**
     * GnssSingleSatCorrectionFlags has valid Excess Path Length field.
     */
    HAS_EXCESS_PATH_LENGTH = 2 /* 0x0002 */,
    /**
     * GnssSingleSatCorrectionFlags has valid Excess Path Length Uncertainty field.
     */
    HAS_EXCESS_PATH_LENGTH_UNC = 4 /* 0x0004 */,
    /**
     * GnssSingleSatCorrectionFlags has valid Reflecting Plane field.
     */
    HAS_REFLECTING_PLANE = 8 /* 0x0008 */,
};

/**
 * A struct containing the characteristics of the reflecting plane that the satellite signal has
 * bounced from.
 *
 * The value is only valid if HAS_REFLECTING_PLANE flag is set. An invalid reflecting plane
 * means either reflection planes serving is not supported or the satellite signal has gone
 * through multiple reflections.
 */
struct ReflectingPlane final {
    /**
     * Represents latitude of the reflecting plane in degrees.
     */
    double latitudeDegrees __attribute__ ((aligned(8)));
    /**
     * Represents longitude of the reflecting plane in degrees.
     */
    double longitudeDegrees __attribute__ ((aligned(8)));
    /**
     * Represents altitude of the reflecting point in the plane in meters above the WGS 84 reference
     * ellipsoid.
     */
    double altitudeMeters __attribute__ ((aligned(8)));
    /**
     * Represents azimuth clockwise from north of the reflecting plane in degrees.
     */
    double azimuthDegrees __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane, latitudeDegrees) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane, longitudeDegrees) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane, altitudeMeters) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane, azimuthDegrees) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane) == 32, "wrong size");
static_assert(__alignof(::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane) == 8, "wrong alignment");

/**
 * A struct with measurement corrections for a single visible satellites
 *
 * The bit mask singleSatCorrectionFlags indicates which correction values are valid in the struct
 */
struct SingleSatCorrection final {
    /**
     * Contains GnssSingleSatCorrectionFlags bits.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags> singleSatCorrectionFlags __attribute__ ((aligned(2)));
    /**
     * Defines the constellation of the given satellite.
     */
    ::android::hardware::gnss::V1_0::GnssConstellationType constellation __attribute__ ((aligned(1)));
    /**
     * Satellite vehicle ID number, as defined in GnssSvInfo::svid
     */
    uint16_t svid __attribute__ ((aligned(2)));
    /**
     * Carrier frequency of the signal to be corrected, for example it can be the
     * GPS center frequency for L1 = 1,575,420,000 Hz, varying GLO channels, etc.
     *
     * For a receiver with capabilities to track multiple frequencies for the same satellite,
     * multiple corrections for the same satellite may be provided.
     */
    float carrierFrequencyHz __attribute__ ((aligned(4)));
    /**
     * The probability that the satellite is estimated to be in Line-of-Sight condition at the given
     * location.
     */
    float probSatIsLos __attribute__ ((aligned(4)));
    /**
     * Excess path length to be subtracted from pseudorange before using it in calculating location.
     *
     * Note this value is NOT to be used to adjust the GnssMeasurementCallback outputs.
     */
    float excessPathLengthMeters __attribute__ ((aligned(4)));
    /**
     * Error estimate (1-sigma) for the Excess path length estimate
     */
    float excessPathLengthUncertaintyMeters __attribute__ ((aligned(4)));
    /**
     * Defines the reflecting plane characteristics such as location and azimuth
     *
     * The value is only valid if HAS_REFLECTING_PLANE flag is set. An invalid reflecting plane
     * means either reflection planes serving is not supported or the satellite signal has gone
     * through multiple reflections.
     */
    ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane reflectingPlane __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection, singleSatCorrectionFlags) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection, constellation) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection, svid) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection, carrierFrequencyHz) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection, probSatIsLos) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection, excessPathLengthMeters) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection, excessPathLengthUncertaintyMeters) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection, reflectingPlane) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection) == 56, "wrong size");
static_assert(__alignof(::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection) == 8, "wrong alignment");

/**
 * A struct containing a set of measurement corrections for all used GNSS satellites at the location
 * specified by latitudeDegrees, longitudeDegrees, altitudeMeters and at the time of week specified
 * toaGpsNanosecondsOfWeek
 */
struct MeasurementCorrections final {
    /**
     * Represents latitude in degrees at which the corrections are computed..
     */
    double latitudeDegrees __attribute__ ((aligned(8)));
    /**
     * Represents longitude in degrees at which the corrections are computed..
     */
    double longitudeDegrees __attribute__ ((aligned(8)));
    /**
     * Represents altitude in meters above the WGS 84 reference ellipsoid at which the corrections
     * are computed.
     */
    double altitudeMeters __attribute__ ((aligned(8)));
    /**
     * Represents the horizontal uncertainty (63% to 68% confidence) in meters on the device
     * position at which the corrections are provided.
     *
     * This value is useful for example to judge how accurate the provided corrections are.
     */
    double horizontalPositionUncertaintyMeters __attribute__ ((aligned(8)));
    /**
     * Represents the vertical uncertainty (63% to 68% confidence) in meters on the device position
     * at which the corrections are provided.
     *
     * This value is useful for example to judge how accurate the provided corrections are.
     */
    double verticalPositionUncertaintyMeters __attribute__ ((aligned(8)));
    /**
     * Time Of Applicability, GPS time of week in nanoseconds.
     */
    uint64_t toaGpsNanosecondsOfWeek __attribute__ ((aligned(8)));
    /**
     * A set of SingleSatCorrection each containing measurement corrections for a satellite in view
     */
    ::android::hardware::hidl_vec<::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection> satCorrections __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections, latitudeDegrees) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections, longitudeDegrees) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections, altitudeMeters) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections, horizontalPositionUncertaintyMeters) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections, verticalPositionUncertaintyMeters) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections, toaGpsNanosecondsOfWeek) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections, satCorrections) == 48, "wrong offset");
static_assert(sizeof(::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections) == 64, "wrong size");
static_assert(__alignof(::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags o);
static inline void PrintTo(::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& o);
static inline void PrintTo(const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& rhs);
static inline bool operator!=(const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& rhs);

static inline std::string toString(const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& o);
static inline void PrintTo(const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& rhs);
static inline bool operator!=(const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& rhs);

static inline std::string toString(const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& o);
static inline void PrintTo(const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& rhs);
static inline bool operator!=(const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_SAT_IS_LOS_PROBABILITY) == static_cast<uint16_t>(::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_SAT_IS_LOS_PROBABILITY)) {
        os += (first ? "" : " | ");
        os += "HAS_SAT_IS_LOS_PROBABILITY";
        first = false;
        flipped |= ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_SAT_IS_LOS_PROBABILITY;
    }
    if ((o & ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_EXCESS_PATH_LENGTH) == static_cast<uint16_t>(::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_EXCESS_PATH_LENGTH)) {
        os += (first ? "" : " | ");
        os += "HAS_EXCESS_PATH_LENGTH";
        first = false;
        flipped |= ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_EXCESS_PATH_LENGTH;
    }
    if ((o & ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_EXCESS_PATH_LENGTH_UNC) == static_cast<uint16_t>(::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_EXCESS_PATH_LENGTH_UNC)) {
        os += (first ? "" : " | ");
        os += "HAS_EXCESS_PATH_LENGTH_UNC";
        first = false;
        flipped |= ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_EXCESS_PATH_LENGTH_UNC;
    }
    if ((o & ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_REFLECTING_PLANE) == static_cast<uint16_t>(::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_REFLECTING_PLANE)) {
        os += (first ? "" : " | ");
        os += "HAS_REFLECTING_PLANE";
        first = false;
        flipped |= ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_REFLECTING_PLANE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_SAT_IS_LOS_PROBABILITY) {
        return "HAS_SAT_IS_LOS_PROBABILITY";
    }
    if (o == ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_EXCESS_PATH_LENGTH) {
        return "HAS_EXCESS_PATH_LENGTH";
    }
    if (o == ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_EXCESS_PATH_LENGTH_UNC) {
        return "HAS_EXCESS_PATH_LENGTH_UNC";
    }
    if (o == ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_REFLECTING_PLANE) {
        return "HAS_REFLECTING_PLANE";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".latitudeDegrees = ";
    os += ::android::hardware::toString(o.latitudeDegrees);
    os += ", .longitudeDegrees = ";
    os += ::android::hardware::toString(o.longitudeDegrees);
    os += ", .altitudeMeters = ";
    os += ::android::hardware::toString(o.altitudeMeters);
    os += ", .azimuthDegrees = ";
    os += ::android::hardware::toString(o.azimuthDegrees);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& rhs) {
    if (lhs.latitudeDegrees != rhs.latitudeDegrees) {
        return false;
    }
    if (lhs.longitudeDegrees != rhs.longitudeDegrees) {
        return false;
    }
    if (lhs.altitudeMeters != rhs.altitudeMeters) {
        return false;
    }
    if (lhs.azimuthDegrees != rhs.azimuthDegrees) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::ReflectingPlane& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".singleSatCorrectionFlags = ";
    os += ::android::hardware::gnss::measurement_corrections::V1_0::toString<::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags>(o.singleSatCorrectionFlags);
    os += ", .constellation = ";
    os += ::android::hardware::gnss::V1_0::toString(o.constellation);
    os += ", .svid = ";
    os += ::android::hardware::toString(o.svid);
    os += ", .carrierFrequencyHz = ";
    os += ::android::hardware::toString(o.carrierFrequencyHz);
    os += ", .probSatIsLos = ";
    os += ::android::hardware::toString(o.probSatIsLos);
    os += ", .excessPathLengthMeters = ";
    os += ::android::hardware::toString(o.excessPathLengthMeters);
    os += ", .excessPathLengthUncertaintyMeters = ";
    os += ::android::hardware::toString(o.excessPathLengthUncertaintyMeters);
    os += ", .reflectingPlane = ";
    os += ::android::hardware::gnss::measurement_corrections::V1_0::toString(o.reflectingPlane);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& rhs) {
    if (lhs.singleSatCorrectionFlags != rhs.singleSatCorrectionFlags) {
        return false;
    }
    if (lhs.constellation != rhs.constellation) {
        return false;
    }
    if (lhs.svid != rhs.svid) {
        return false;
    }
    if (lhs.carrierFrequencyHz != rhs.carrierFrequencyHz) {
        return false;
    }
    if (lhs.probSatIsLos != rhs.probSatIsLos) {
        return false;
    }
    if (lhs.excessPathLengthMeters != rhs.excessPathLengthMeters) {
        return false;
    }
    if (lhs.excessPathLengthUncertaintyMeters != rhs.excessPathLengthUncertaintyMeters) {
        return false;
    }
    if (lhs.reflectingPlane != rhs.reflectingPlane) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".latitudeDegrees = ";
    os += ::android::hardware::toString(o.latitudeDegrees);
    os += ", .longitudeDegrees = ";
    os += ::android::hardware::toString(o.longitudeDegrees);
    os += ", .altitudeMeters = ";
    os += ::android::hardware::toString(o.altitudeMeters);
    os += ", .horizontalPositionUncertaintyMeters = ";
    os += ::android::hardware::toString(o.horizontalPositionUncertaintyMeters);
    os += ", .verticalPositionUncertaintyMeters = ";
    os += ::android::hardware::toString(o.verticalPositionUncertaintyMeters);
    os += ", .toaGpsNanosecondsOfWeek = ";
    os += ::android::hardware::toString(o.toaGpsNanosecondsOfWeek);
    os += ", .satCorrections = ";
    os += ::android::hardware::toString(o.satCorrections);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& rhs) {
    if (lhs.latitudeDegrees != rhs.latitudeDegrees) {
        return false;
    }
    if (lhs.longitudeDegrees != rhs.longitudeDegrees) {
        return false;
    }
    if (lhs.altitudeMeters != rhs.altitudeMeters) {
        return false;
    }
    if (lhs.horizontalPositionUncertaintyMeters != rhs.horizontalPositionUncertaintyMeters) {
        return false;
    }
    if (lhs.verticalPositionUncertaintyMeters != rhs.verticalPositionUncertaintyMeters) {
        return false;
    }
    if (lhs.toaGpsNanosecondsOfWeek != rhs.toaGpsNanosecondsOfWeek) {
        return false;
    }
    if (lhs.satCorrections != rhs.satCorrections) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& lhs, const ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace measurement_corrections
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
template<> inline constexpr std::array<::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags, 4> hidl_enum_values<::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags> = {
    ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_SAT_IS_LOS_PROBABILITY,
    ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_EXCESS_PATH_LENGTH,
    ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_EXCESS_PATH_LENGTH_UNC,
    ::android::hardware::gnss::measurement_corrections::V1_0::GnssSingleSatCorrectionFlags::HAS_REFLECTING_PLANE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_0_TYPES_H
