#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_TYPES_H

#include <android/hardware/gnss/2.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_1 {

// Forward declaration for forward reference support:
struct GnssSignalType;

/**
 * Represents a GNSS signal type.
 */
struct GnssSignalType final {
    /**
     * Constellation type of the SV that transmits the signal.
     */
    ::android::hardware::gnss::V2_0::GnssConstellationType constellation __attribute__ ((aligned(1)));
    /**
     * Carrier frequency in Hz of the signal.
     */
    double carrierFrequencyHz __attribute__ ((aligned(8)));
    /**
     * The type of code of the GNSS signal.
     *
     * This is used to specify the observation descriptor defined in GNSS Observation Data File
     * Header Section Description in the RINEX standard (Version 3.XX). In RINEX Version 3.03,
     * in Appendix Table A2 Attributes are listed as uppercase letters (for instance, "A" for
     * "A channel").
     *
     * See the comment of @2.0::IGnssMeasurementCallback.GnssMeasurement.codeType for more details.
     */
    ::android::hardware::hidl_string codeType __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::gnss::V2_1::GnssSignalType, constellation) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V2_1::GnssSignalType, carrierFrequencyHz) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::gnss::V2_1::GnssSignalType, codeType) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::gnss::V2_1::GnssSignalType) == 32, "wrong size");
static_assert(__alignof(::android::hardware::gnss::V2_1::GnssSignalType) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::gnss::V2_1::GnssSignalType& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_1::GnssSignalType& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_1::GnssSignalType& lhs, const ::android::hardware::gnss::V2_1::GnssSignalType& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_1::GnssSignalType& lhs, const ::android::hardware::gnss::V2_1::GnssSignalType& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::gnss::V2_1::GnssSignalType& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".constellation = ";
    os += ::android::hardware::gnss::V2_0::toString(o.constellation);
    os += ", .carrierFrequencyHz = ";
    os += ::android::hardware::toString(o.carrierFrequencyHz);
    os += ", .codeType = ";
    os += ::android::hardware::toString(o.codeType);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_1::GnssSignalType& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_1::GnssSignalType& lhs, const ::android::hardware::gnss::V2_1::GnssSignalType& rhs) {
    if (lhs.constellation != rhs.constellation) {
        return false;
    }
    if (lhs.carrierFrequencyHz != rhs.carrierFrequencyHz) {
        return false;
    }
    if (lhs.codeType != rhs.codeType) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_1::GnssSignalType& lhs, const ::android::hardware::gnss::V2_1::GnssSignalType& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_1
}  // namespace gnss
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_TYPES_H
