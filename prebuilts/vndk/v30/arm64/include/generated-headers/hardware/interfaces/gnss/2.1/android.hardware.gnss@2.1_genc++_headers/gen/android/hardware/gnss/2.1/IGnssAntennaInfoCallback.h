#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IGNSSANTENNAINFOCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IGNSSANTENNAINFOCALLBACK_H

#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_1 {

/**
 * The callback interface to report GNSS antenna information from the HAL.
 */
struct IGnssAntennaInfoCallback : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@2.1::IGnssAntennaInfoCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    struct Row;
    struct Coord;
    struct GnssAntennaInfo;

    /**
     * A row of doubles. This is used to represent a row in a 2D array, which are used to
     * characterize the phase center variation corrections and signal gain corrections.
     */
    struct Row final {
        ::android::hardware::hidl_vec<double> row __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row, row) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row) == 8, "wrong alignment");

    /**
     * A point in 3D space, with associated uncertainty.
     */
    struct Coord final {
        double x __attribute__ ((aligned(8)));
        double xUncertainty __attribute__ ((aligned(8)));
        double y __attribute__ ((aligned(8)));
        double yUncertainty __attribute__ ((aligned(8)));
        double z __attribute__ ((aligned(8)));
        double zUncertainty __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord, x) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord, xUncertainty) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord, y) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord, yUncertainty) == 24, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord, z) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord, zUncertainty) == 40, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord) == 48, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord) == 8, "wrong alignment");

    struct GnssAntennaInfo final {
        /**
         * The carrier frequency in MHz.
         */
        double carrierFrequencyMHz __attribute__ ((aligned(8)));
        /**
         * Phase center offset (PCO) with associated 1-sigma uncertainty. PCO is defined with
         * respect to the origin of the Android sensor coordinate system, e.g., center of primary
         * screen for mobiles - see sensor or form factor documents for details.
         */
        ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord phaseCenterOffsetCoordinateMillimeters __attribute__ ((aligned(8)));
        /**
         * 2D vectors representing the phase center variation (PCV) corrections, in
         * millimeters, at regularly spaced azimuthal angle (theta) and zenith angle
         * (phi). The PCV correction is added to the phase measurement to obtain the
         * corrected value.
         *
         * The azimuthal angle, theta, is defined with respect to the X axis of the
         * Android sensor coordinate system, increasing toward the Y axis. The zenith
         * angle, phi, is defined with respect to the Z axis of the Android Sensor
         * coordinate system, increasing toward the X-Y plane.
         *
         * Each row vector (outer vectors) represents a fixed theta. The first row
         * corresponds to a theta angle of 0 degrees. The last row corresponds to a
         * theta angle of (360 - deltaTheta) degrees, where deltaTheta is the regular
         * spacing between azimuthal angles, i.e., deltaTheta = 360 / (number of rows).
         *
         * The columns (inner vectors) represent fixed zenith angles, beginning at 0
         * degrees and ending at 180 degrees. They are separated by deltaPhi, the regular
         * spacing between zenith angles, i.e., deltaPhi = 180 / (number of columns - 1).
         *
         * This field is optional, i.e., an empty vector.
         */
        ::android::hardware::hidl_vec<::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row> phaseCenterVariationCorrectionMillimeters __attribute__ ((aligned(8)));
        /**
         * 2D vectors of 1-sigma uncertainty in millimeters associated with the PCV
         * correction values.
         *
         * This field is optional, i.e., an empty vector.
         */
        ::android::hardware::hidl_vec<::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row> phaseCenterVariationCorrectionUncertaintyMillimeters __attribute__ ((aligned(8)));
        /**
         * 2D vectors representing the signal gain corrections at regularly spaced
         * azimuthal angle (theta) and zenith angle (phi). The values are calculated or
         * measured at the antenna feed point without considering the radio and receiver
         * noise figure and path loss contribution, in dBi, i.e., decibel over isotropic
         * antenna with the same total power. The signal gain correction is added the
         * signal gain measurement to obtain the corrected value.
         *
         * The azimuthal angle, theta, is defined with respect to the X axis of the
         * Android sensor coordinate system, increasing toward the Y axis. The zenith
         * angle, phi, is defined with respect to the Z axis of the Android Sensor
         * coordinate system, increasing toward the X-Y plane.
         *
         * Each row vector (outer vectors) represents a fixed theta. The first row
         * corresponds to a theta angle of 0 degrees. The last row corresponds to a
         * theta angle of (360 - deltaTheta) degrees, where deltaTheta is the regular
         * spacing between azimuthal angles, i.e., deltaTheta = 360 / (number of rows).
         *
         * The columns (inner vectors) represent fixed zenith angles, beginning at 0
         * degrees and ending at 180 degrees. They are separated by deltaPhi, the regular
         * spacing between zenith angles, i.e., deltaPhi = 180 / (number of columns - 1).
         *
         * This field is optional, i.e., an empty vector.
         */
        ::android::hardware::hidl_vec<::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row> signalGainCorrectionDbi __attribute__ ((aligned(8)));
        /**
         * 2D vectors of 1-sigma uncertainty in dBi associated with the signal
         * gain correction values.
         *
         * This field is optional, i.e., an empty vector.
         */
        ::android::hardware::hidl_vec<::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row> signalGainCorrectionUncertaintyDbi __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo, carrierFrequencyMHz) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo, phaseCenterOffsetCoordinateMillimeters) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo, phaseCenterVariationCorrectionMillimeters) == 56, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo, phaseCenterVariationCorrectionUncertaintyMillimeters) == 72, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo, signalGainCorrectionDbi) == 88, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo, signalGainCorrectionUncertaintyDbi) == 104, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo) == 120, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Called when on connection, and on known-change to these values, such as upon a known
     * GNSS RF antenna tuning change, or a foldable device state change.
     *
     * This is optional. It can never be called if the GNSS antenna information is not
     * available.
     */
    virtual ::android::hardware::Return<void> gnssAntennaInfoCb(const ::android::hardware::hidl_vec<::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo>& gnssAntennaInfos) = 0;

    /**
     * Return callback for interfaceChain
     */
    using interfaceChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& descriptors)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     ["android.hardware.foo@1.0::IChild",
     *      "android.hardware.foo@1.0::IParent"
     *      "android.hidl.base@1.0::IBase"]
     *
     * @return descriptors a vector of descriptors of the run-time type of the
     *         object.
     */
    virtual ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;

    /*
     * Emit diagnostic information to the given file.
     *
     * Optionally overriden.
     *
     * @param fd      File descriptor to dump data to.
     *                Must only be used for the duration of this call.
     * @param options Arguments for debugging.
     *                Must support empty for default debug information.
     */
    virtual ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;

    /**
     * Return callback for interfaceDescriptor
     */
    using interfaceDescriptor_cb = std::function<void(const ::android::hardware::hidl_string& descriptor)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceDescriptor on an IChild object must yield
     *     "android.hardware.foo@1.0::IChild"
     *
     * @return descriptor a descriptor of the run-time type of the
     *         object (the first element of the vector returned by
     *         interfaceChain())
     */
    virtual ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;

    /**
     * Return callback for getHashChain
     */
    using getHashChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 32>>& hashchain)>;
    /*
     * Returns hashes of the source HAL files that define the interfaces of the
     * runtime type information on the object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     [(hash of IChild.hal),
     *      (hash of IParent.hal)
     *      (hash of IBase.hal)].
     *
     * SHA-256 is used as the hashing algorithm. Each hash has 32 bytes
     * according to SHA-256 standard.
     *
     * @return hashchain a vector of SHA-1 digests
     */
    virtual ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;

    /*
     * This method trigger the interface to enable/disable instrumentation based
     * on system property hal.instrumentation.enable.
     */
    virtual ::android::hardware::Return<void> setHALInstrumentation() override;

    /*
     * Registers a death recipient, to be called when the process hosting this
     * interface dies.
     *
     * @param recipient a hidl_death_recipient callback object
     * @param cookie a cookie that must be returned with the callback
     * @return success whether the death recipient was registered successfully.
     */
    virtual ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;

    /*
     * Provides way to determine if interface is running without requesting
     * any functionality.
     */
    virtual ::android::hardware::Return<void> ping() override;

    /**
     * Return callback for getDebugInfo
     */
    using getDebugInfo_cb = std::function<void(const ::android::hidl::base::V1_0::DebugInfo& info)>;
    /*
     * Get debug information on references on this interface.
     * @return info debugging information. See comments of DebugInfo.
     */
    virtual ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;

    /*
     * This method notifies the interface that one or more system properties
     * have changed. The default implementation calls
     * (C++)  report_sysprop_change() in libcutils or
     * (Java) android.os.SystemProperties.reportSyspropChanged,
     * which in turn calls a set of registered callbacks (eg to update trace
     * tags).
     */
    virtual ::android::hardware::Return<void> notifySyspropsChanged() override;

    /*
     * Unregisters the registered death recipient. If this service was registered
     * multiple times with the same exact death recipient, this unlinks the most
     * recently registered one.
     *
     * @param recipient a previously registered hidl_death_recipient callback
     * @return success whether the death recipient was unregistered successfully.
     */
    virtual ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

    // cast static functions
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

    // helper methods for interactions with the hwservicemanager
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is currently not available or not in the VINTF manifest on a Trebilized
     * device, this will return nullptr. This is useful when you don't want to block
     * during device boot. If getStub is true, this will try to return an unwrapped
     * passthrough implementation in the same process. This is useful when getting an
     * implementation from the same partition/compilation group.
     *
     * In general, prefer getService(std::string,bool)
     */
    static ::android::sp<IGnssAntennaInfoCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssAntennaInfoCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssAntennaInfoCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssAntennaInfoCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnssAntennaInfoCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssAntennaInfoCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssAntennaInfoCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssAntennaInfoCallback> getService(bool getStub) { return getService("default", getStub); }
    /**
     * Registers a service with the service manager. For Trebilized devices, the service
     * must also be in the VINTF manifest.
     */
    __attribute__ ((warn_unused_result))::android::status_t registerAsService(const std::string &serviceName="default");
    /**
     * Registers for notifications for when a service is registered.
     */
    static bool registerForNotifications(
            const std::string &serviceName,
            const ::android::sp<::android::hidl::manager::V1_0::IServiceNotification> &notification);
};

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& rhs);

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& rhs);

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".row = ";
    os += ::android::hardware::toString(o.row);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& rhs) {
    if (lhs.row != rhs.row) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".x = ";
    os += ::android::hardware::toString(o.x);
    os += ", .xUncertainty = ";
    os += ::android::hardware::toString(o.xUncertainty);
    os += ", .y = ";
    os += ::android::hardware::toString(o.y);
    os += ", .yUncertainty = ";
    os += ::android::hardware::toString(o.yUncertainty);
    os += ", .z = ";
    os += ::android::hardware::toString(o.z);
    os += ", .zUncertainty = ";
    os += ::android::hardware::toString(o.zUncertainty);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& rhs) {
    if (lhs.x != rhs.x) {
        return false;
    }
    if (lhs.xUncertainty != rhs.xUncertainty) {
        return false;
    }
    if (lhs.y != rhs.y) {
        return false;
    }
    if (lhs.yUncertainty != rhs.yUncertainty) {
        return false;
    }
    if (lhs.z != rhs.z) {
        return false;
    }
    if (lhs.zUncertainty != rhs.zUncertainty) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Coord& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".carrierFrequencyMHz = ";
    os += ::android::hardware::toString(o.carrierFrequencyMHz);
    os += ", .phaseCenterOffsetCoordinateMillimeters = ";
    os += ::android::hardware::gnss::V2_1::toString(o.phaseCenterOffsetCoordinateMillimeters);
    os += ", .phaseCenterVariationCorrectionMillimeters = ";
    os += ::android::hardware::toString(o.phaseCenterVariationCorrectionMillimeters);
    os += ", .phaseCenterVariationCorrectionUncertaintyMillimeters = ";
    os += ::android::hardware::toString(o.phaseCenterVariationCorrectionUncertaintyMillimeters);
    os += ", .signalGainCorrectionDbi = ";
    os += ::android::hardware::toString(o.signalGainCorrectionDbi);
    os += ", .signalGainCorrectionUncertaintyDbi = ";
    os += ::android::hardware::toString(o.signalGainCorrectionUncertaintyDbi);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& rhs) {
    if (lhs.carrierFrequencyMHz != rhs.carrierFrequencyMHz) {
        return false;
    }
    if (lhs.phaseCenterOffsetCoordinateMillimeters != rhs.phaseCenterOffsetCoordinateMillimeters) {
        return false;
    }
    if (lhs.phaseCenterVariationCorrectionMillimeters != rhs.phaseCenterVariationCorrectionMillimeters) {
        return false;
    }
    if (lhs.phaseCenterVariationCorrectionUncertaintyMillimeters != rhs.phaseCenterVariationCorrectionUncertaintyMillimeters) {
        return false;
    }
    if (lhs.signalGainCorrectionDbi != rhs.signalGainCorrectionDbi) {
        return false;
    }
    if (lhs.signalGainCorrectionUncertaintyDbi != rhs.signalGainCorrectionUncertaintyDbi) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& lhs, const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_1
}  // namespace gnss
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IGNSSANTENNAINFOCALLBACK_H
