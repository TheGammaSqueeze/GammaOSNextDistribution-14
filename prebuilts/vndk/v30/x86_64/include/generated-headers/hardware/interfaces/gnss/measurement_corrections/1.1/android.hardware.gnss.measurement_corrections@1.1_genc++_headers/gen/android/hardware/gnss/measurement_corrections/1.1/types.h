#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_1_TYPES_H

#include <android/hardware/gnss/measurement_corrections/1.0/types.h>
#include <android/hardware/gnss/2.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
namespace V1_1 {

// Forward declaration for forward reference support:
struct MeasurementCorrections;
struct SingleSatCorrection;

/**
 * A struct containing a set of measurement corrections for all used GNSS satellites at the location
 * specified by latitudeDegrees, longitudeDegrees, altitudeMeters and at the time of week specified
 * toaGpsNanosecondsOfWeek. The v1_0.satCorrections field is deprecated and is no longer used by
 * framework.
 */
struct MeasurementCorrections final {
    ::android::hardware::gnss::measurement_corrections::V1_0::MeasurementCorrections v1_0 __attribute__ ((aligned(8)));
    /**
     * Boolean indicating if environment bearing is available.
     */
    bool hasEnvironmentBearing __attribute__ ((aligned(1)));
    /**
     * Environment bearing in degrees clockwise from true North (0.0 to 360.0], in direction of
     * user motion. Environment bearing is provided when it is known with high probability that
     * velocity is aligned with an environment feature, such as a building or road.
     *
     * If user speed is zero, environmentBearingDegrees represents bearing of most recent speed
     * that was > 0.
     *
     * As position approaches another road, environmentBearingUncertaintyDegrees will grow, and at
     * some stage hasEnvironmentBearing = false.
     *
     * As position moves towards an open area, environmentBearingUncertaintyDegrees will grow, and
     * at some stage hasEnvironmentBearing = false.
     *
     * If the road is curved in the vicinity of the user location, then
     * environmentBearingUncertaintyDegrees will include the amount by which the road direction
     * changes in the area of position uncertainty.
     *
     * hasEnvironmentBearing should be checked to verify the environment bearing is available
     * before calling this method. The value is undefined if hasEnvironmentBearing is false.
     */
    float environmentBearingDegrees __attribute__ ((aligned(4)));
    /**
     * Environment bearing uncertainty [0 to 180]. It represents the standard deviation of the
     * physical structure in the circle of position uncertainty. hasEnvironmentBearing becomes false
     * as the uncertainty value passes a predefined threshold depending on the physical structure
     * around the user.
     *
     * hasEnvironmentBearing should be checked to verify the environment bearing is available
     * before calling this method. The value is undefined if hasEnvironmentBearing is false.
     */
    float environmentBearingUncertaintyDegrees __attribute__ ((aligned(4)));
    /**
     * A set of SingleSatCorrection each containing measurement corrections for a satellite in view
     */
    ::android::hardware::hidl_vec<::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection> satCorrections __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections, v1_0) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections, hasEnvironmentBearing) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections, environmentBearingDegrees) == 68, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections, environmentBearingUncertaintyDegrees) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections, satCorrections) == 80, "wrong offset");
static_assert(sizeof(::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections) == 96, "wrong size");
static_assert(__alignof(::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections) == 8, "wrong alignment");

/**
 * A struct with measurement corrections for a single visible satellites, updating the
 * GnssConstellationType to 2.0, which supports IRNSS. The v1_0.constellation is deprecated and is
 * no longer used by framework.
 *
 * The bit mask singleSatCorrectionFlags indicates which correction values are valid in the struct
 */
struct SingleSatCorrection final {
    ::android::hardware::gnss::measurement_corrections::V1_0::SingleSatCorrection v1_0 __attribute__ ((aligned(8)));
    /**
     * Defines the constellation of the given satellite.
     */
    ::android::hardware::gnss::V2_0::GnssConstellationType constellation __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection, v1_0) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection, constellation) == 56, "wrong offset");
static_assert(sizeof(::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection) == 64, "wrong size");
static_assert(__alignof(::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& o);
static inline void PrintTo(const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& lhs, const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& rhs);
static inline bool operator!=(const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& lhs, const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& rhs);

static inline std::string toString(const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& o);
static inline void PrintTo(const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& lhs, const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& rhs);
static inline bool operator!=(const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& lhs, const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v1_0 = ";
    os += ::android::hardware::gnss::measurement_corrections::V1_0::toString(o.v1_0);
    os += ", .hasEnvironmentBearing = ";
    os += ::android::hardware::toString(o.hasEnvironmentBearing);
    os += ", .environmentBearingDegrees = ";
    os += ::android::hardware::toString(o.environmentBearingDegrees);
    os += ", .environmentBearingUncertaintyDegrees = ";
    os += ::android::hardware::toString(o.environmentBearingUncertaintyDegrees);
    os += ", .satCorrections = ";
    os += ::android::hardware::toString(o.satCorrections);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& lhs, const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& rhs) {
    if (lhs.v1_0 != rhs.v1_0) {
        return false;
    }
    if (lhs.hasEnvironmentBearing != rhs.hasEnvironmentBearing) {
        return false;
    }
    if (lhs.environmentBearingDegrees != rhs.environmentBearingDegrees) {
        return false;
    }
    if (lhs.environmentBearingUncertaintyDegrees != rhs.environmentBearingUncertaintyDegrees) {
        return false;
    }
    if (lhs.satCorrections != rhs.satCorrections) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& lhs, const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v1_0 = ";
    os += ::android::hardware::gnss::measurement_corrections::V1_0::toString(o.v1_0);
    os += ", .constellation = ";
    os += ::android::hardware::gnss::V2_0::toString(o.constellation);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& lhs, const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& rhs) {
    if (lhs.v1_0 != rhs.v1_0) {
        return false;
    }
    if (lhs.constellation != rhs.constellation) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& lhs, const ::android::hardware::gnss::measurement_corrections::V1_1::SingleSatCorrection& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_1
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_1_TYPES_H
