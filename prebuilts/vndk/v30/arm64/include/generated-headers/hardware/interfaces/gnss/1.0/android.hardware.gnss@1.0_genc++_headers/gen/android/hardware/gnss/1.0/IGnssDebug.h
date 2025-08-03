#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSDEBUG_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSDEBUG_H

#include <android/hardware/gnss/1.0/types.h>
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
namespace V1_0 {

/**
 * Extended interface for DEBUG support.
 */
struct IGnssDebug : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@1.0::IGnssDebug"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class SatelliteEphemerisType : uint8_t;
    enum class SatelliteEphemerisSource : uint8_t;
    enum class SatelliteEphemerisHealth : uint8_t;
    struct PositionDebug;
    struct TimeDebug;
    struct SatelliteData;
    struct DebugData;

    enum class SatelliteEphemerisType : uint8_t {
        /**
         * Ephemeris is known for this satellite.
         */
        EPHEMERIS = 0,
        /**
         * Ephemeris is not known, but Almanac (approximate location) is known.
         */
        ALMANAC_ONLY = 1 /* ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType.EPHEMERIS implicitly + 1 */,
        /**
         * Both ephemeris & almanac are not known (e.g. during a cold start
         * blind search.)
         */
        NOT_AVAILABLE = 2 /* ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType.ALMANAC_ONLY implicitly + 1 */,
    };

    enum class SatelliteEphemerisSource : uint8_t {
        /**
         * The ephemeris (or almanac only) information was demodulated from the
         * signal received on the device
         */
        DEMODULATED = 0,
        /**
         * The ephemeris (or almanac only) information was received from a SUPL
         * server.
         */
        SUPL_PROVIDED = 1 /* ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource.DEMODULATED implicitly + 1 */,
        /**
         * The ephemeris (or almanac only) information was provided by another
         * server.
         */
        OTHER_SERVER_PROVIDED = 2 /* ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource.SUPL_PROVIDED implicitly + 1 */,
        /**
         * The ephemeris (or almanac only) information was provided by another
         * method, e.g. injected via a local debug tool, from build defaults
         * (e.g. almanac), or is from a satellite
         * with SatelliteEphemerisType::NOT_AVAILABLE.
         */
        OTHER = 3 /* ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource.OTHER_SERVER_PROVIDED implicitly + 1 */,
    };

    enum class SatelliteEphemerisHealth : uint8_t {
        /**
         * The ephemeris is known good.
         */
        GOOD = 0,
        /**
         * The ephemeris is known bad.
         */
        BAD = 1 /* ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth.GOOD implicitly + 1 */,
        /**
         * The ephemeris is unknown to be good or bad.
         */
        UNKNOWN = 2 /* ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth.BAD implicitly + 1 */,
    };

    /**
     * Provides the current best known position from any
     * source (GNSS or injected assistance).
     */
    struct PositionDebug final {
        /**
         * Validity of the data in this struct. False only if no
         * latitude/longitude information is known.
         */
        bool valid __attribute__ ((aligned(1)));
        /**
         * Latitude expressed in degrees
         */
        double latitudeDegrees __attribute__ ((aligned(8)));
        /**
         * Longitude expressed in degrees
         */
        double longitudeDegrees __attribute__ ((aligned(8)));
        /**
         * Altitude above ellipsoid expressed in meters
         */
        float altitudeMeters __attribute__ ((aligned(4)));
        /**
         * Represents horizontal speed in meters per second.
         */
        float speedMetersPerSec __attribute__ ((aligned(4)));
        /**
         * Represents heading in degrees.
         */
        float bearingDegrees __attribute__ ((aligned(4)));
        /**
         * Estimated horizontal accuracy of position expressed in meters,
         * radial, 68% confidence.
         */
        double horizontalAccuracyMeters __attribute__ ((aligned(8)));
        /**
         * Estimated vertical accuracy of position expressed in meters, with
         * 68% confidence.
         */
        double verticalAccuracyMeters __attribute__ ((aligned(8)));
        /**
         * Estimated speed accuracy in meters per second with 68% confidence.
         */
        double speedAccuracyMetersPerSecond __attribute__ ((aligned(8)));
        /**
         * estimated bearing accuracy degrees with 68% confidence.
         */
        double bearingAccuracyDegrees __attribute__ ((aligned(8)));
        /**
         * Time duration before this report that this position information was
         * valid.  This can, for example, be a previous injected location with
         * an age potentially thousands of seconds old, or
         * extrapolated to the current time (with appropriately increased
         * accuracy estimates), with a (near) zero age.
         */
        float ageSeconds __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, valid) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, latitudeDegrees) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, longitudeDegrees) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, altitudeMeters) == 24, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, speedMetersPerSec) == 28, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, bearingDegrees) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, horizontalAccuracyMeters) == 40, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, verticalAccuracyMeters) == 48, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, speedAccuracyMetersPerSecond) == 56, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, bearingAccuracyDegrees) == 64, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug, ageSeconds) == 72, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug) == 80, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug) == 8, "wrong alignment");

    /**
     * Provides the current best known UTC time estimate.
     * If no fresh information is available, e.g. after a delete all,
     * then whatever the effective defaults are on the device must be
     * provided (e.g. Jan. 1, 2017, with an uncertainty of 5 years) expressed
     * in the specified units.
     */
    struct TimeDebug final {
        /**
         * UTC time estimate.
         */
        int64_t timeEstimate __attribute__ ((aligned(8)));
        /**
         * 68% error estimate in time.
         */
        float timeUncertaintyNs __attribute__ ((aligned(4)));
        /**
         * 68% error estimate in local clock drift,
         * in nanoseconds per second (also known as parts per billion - ppb.)
         */
        float frequencyUncertaintyNsPerSec __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug, timeEstimate) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug, timeUncertaintyNs) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug, frequencyUncertaintyNsPerSec) == 12, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug) == 8, "wrong alignment");

    /**
     * Provides a single satellite info that has decoded navigation data.
     */
    struct SatelliteData final {
        /**
         * Satellite vehicle ID number
         */
        int16_t svid __attribute__ ((aligned(2)));
        /**
         * Defines the constellation type of the given SV.
         */
        ::android::hardware::gnss::V1_0::GnssConstellationType constellation __attribute__ ((aligned(1)));
        /**
         * Defines the standard broadcast ephemeris or almanac availability for
         * the satellite.  To report status of predicted orbit and clock
         * information, see the serverPrediction fields below.
         */
        ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType ephemerisType __attribute__ ((aligned(1)));
        /**
         * Defines the ephemeris source of the satellite.
         */
        ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource ephemerisSource __attribute__ ((aligned(1)));
        /**
         * Defines whether the satellite is known healthy
         * (safe for use in location calculation.)
         */
        ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth ephemerisHealth __attribute__ ((aligned(1)));
        /**
         * Time duration from this report (current time), minus the
         * effective time of the ephemeris source (e.g. TOE, TOA.)
         * Set to 0 when ephemerisType is NOT_AVAILABLE.
         */
        float ephemerisAgeSeconds __attribute__ ((aligned(4)));
        /**
         * True if a server has provided a predicted orbit and clock model for
         * this satellite.
         */
        bool serverPredictionIsAvailable __attribute__ ((aligned(1)));
        /**
         * Time duration from this report (current time) minus the time of the
         * start of the server predicted information.  For example, a 1 day
         * old prediction would be reported as 86400 seconds here.
         */
        float serverPredictionAgeSeconds __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData, svid) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData, constellation) == 2, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData, ephemerisType) == 3, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData, ephemerisSource) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData, ephemerisHealth) == 5, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData, ephemerisAgeSeconds) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData, serverPredictionIsAvailable) == 12, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData, serverPredictionAgeSeconds) == 16, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData) == 20, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData) == 4, "wrong alignment");

    /**
     * Provides a set of debug information that is filled by the GNSS chipset
     * when the method getDebugData() is invoked.
     */
    struct DebugData final {
        /**
         * Current best known position.
         */
        ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug position __attribute__ ((aligned(8)));
        /**
         * Current best know time estimate
         */
        ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug time __attribute__ ((aligned(8)));
        /**
         * Provides a list of the available satellite data, for all
         * satellites and constellations the device can track,
         * including GnssConstellationType UNKNOWN.
         */
        ::android::hardware::hidl_vec<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData> satelliteDataArray __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::DebugData, position) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::DebugData, time) == 80, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssDebug::DebugData, satelliteDataArray) == 96, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IGnssDebug::DebugData) == 112, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IGnssDebug::DebugData) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getDebugData
     */
    using getDebugData_cb = std::function<void(const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& debugData)>;
    /**
     * This methods requests position, time and satellite ephemeris debug information
     * from the HAL.
     *
     * @return ret debugData information from GNSS Hal that contains the current best
     * known position, best known time estimate and a complete list of
     * constellations that the device can track.
     */
    virtual ::android::hardware::Return<void> getDebugData(getDebugData_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssDebug>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IGnssDebug>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssDebug>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IGnssDebug> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssDebug> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssDebug> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssDebug> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnssDebug> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssDebug> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssDebug> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssDebug> getService(bool getStub) { return getService("default", getStub); }
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

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& rhs);

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& rhs);

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& rhs);

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IGnssDebug>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::EPHEMERIS) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::EPHEMERIS)) {
        os += (first ? "" : " | ");
        os += "EPHEMERIS";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::EPHEMERIS;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::ALMANAC_ONLY) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::ALMANAC_ONLY)) {
        os += (first ? "" : " | ");
        os += "ALMANAC_ONLY";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::ALMANAC_ONLY;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::NOT_AVAILABLE) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::NOT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "NOT_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::NOT_AVAILABLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::EPHEMERIS) {
        return "EPHEMERIS";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::ALMANAC_ONLY) {
        return "ALMANAC_ONLY";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::NOT_AVAILABLE) {
        return "NOT_AVAILABLE";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::DEMODULATED) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::DEMODULATED)) {
        os += (first ? "" : " | ");
        os += "DEMODULATED";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::DEMODULATED;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::SUPL_PROVIDED) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::SUPL_PROVIDED)) {
        os += (first ? "" : " | ");
        os += "SUPL_PROVIDED";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::SUPL_PROVIDED;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::OTHER_SERVER_PROVIDED) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::OTHER_SERVER_PROVIDED)) {
        os += (first ? "" : " | ");
        os += "OTHER_SERVER_PROVIDED";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::OTHER_SERVER_PROVIDED;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::OTHER) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::OTHER)) {
        os += (first ? "" : " | ");
        os += "OTHER";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::OTHER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::DEMODULATED) {
        return "DEMODULATED";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::SUPL_PROVIDED) {
        return "SUPL_PROVIDED";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::OTHER_SERVER_PROVIDED) {
        return "OTHER_SERVER_PROVIDED";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::OTHER) {
        return "OTHER";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::GOOD) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::GOOD)) {
        os += (first ? "" : " | ");
        os += "GOOD";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::GOOD;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::BAD) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::BAD)) {
        os += (first ? "" : " | ");
        os += "BAD";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::BAD;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::UNKNOWN) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::GOOD) {
        return "GOOD";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::BAD) {
        return "BAD";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::UNKNOWN) {
        return "UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".valid = ";
    os += ::android::hardware::toString(o.valid);
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
    os += ", .ageSeconds = ";
    os += ::android::hardware::toString(o.ageSeconds);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& rhs) {
    if (lhs.valid != rhs.valid) {
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
    if (lhs.ageSeconds != rhs.ageSeconds) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::PositionDebug& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timeEstimate = ";
    os += ::android::hardware::toString(o.timeEstimate);
    os += ", .timeUncertaintyNs = ";
    os += ::android::hardware::toString(o.timeUncertaintyNs);
    os += ", .frequencyUncertaintyNsPerSec = ";
    os += ::android::hardware::toString(o.frequencyUncertaintyNsPerSec);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& rhs) {
    if (lhs.timeEstimate != rhs.timeEstimate) {
        return false;
    }
    if (lhs.timeUncertaintyNs != rhs.timeUncertaintyNs) {
        return false;
    }
    if (lhs.frequencyUncertaintyNsPerSec != rhs.frequencyUncertaintyNsPerSec) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::TimeDebug& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".svid = ";
    os += ::android::hardware::toString(o.svid);
    os += ", .constellation = ";
    os += ::android::hardware::gnss::V1_0::toString(o.constellation);
    os += ", .ephemerisType = ";
    os += ::android::hardware::gnss::V1_0::toString(o.ephemerisType);
    os += ", .ephemerisSource = ";
    os += ::android::hardware::gnss::V1_0::toString(o.ephemerisSource);
    os += ", .ephemerisHealth = ";
    os += ::android::hardware::gnss::V1_0::toString(o.ephemerisHealth);
    os += ", .ephemerisAgeSeconds = ";
    os += ::android::hardware::toString(o.ephemerisAgeSeconds);
    os += ", .serverPredictionIsAvailable = ";
    os += ::android::hardware::toString(o.serverPredictionIsAvailable);
    os += ", .serverPredictionAgeSeconds = ";
    os += ::android::hardware::toString(o.serverPredictionAgeSeconds);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& rhs) {
    if (lhs.svid != rhs.svid) {
        return false;
    }
    if (lhs.constellation != rhs.constellation) {
        return false;
    }
    if (lhs.ephemerisType != rhs.ephemerisType) {
        return false;
    }
    if (lhs.ephemerisSource != rhs.ephemerisSource) {
        return false;
    }
    if (lhs.ephemerisHealth != rhs.ephemerisHealth) {
        return false;
    }
    if (lhs.ephemerisAgeSeconds != rhs.ephemerisAgeSeconds) {
        return false;
    }
    if (lhs.serverPredictionIsAvailable != rhs.serverPredictionIsAvailable) {
        return false;
    }
    if (lhs.serverPredictionAgeSeconds != rhs.serverPredictionAgeSeconds) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".position = ";
    os += ::android::hardware::gnss::V1_0::toString(o.position);
    os += ", .time = ";
    os += ::android::hardware::gnss::V1_0::toString(o.time);
    os += ", .satelliteDataArray = ";
    os += ::android::hardware::toString(o.satelliteDataArray);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& rhs) {
    if (lhs.position != rhs.position) {
        return false;
    }
    if (lhs.time != rhs.time) {
        return false;
    }
    if (lhs.satelliteDataArray != rhs.satelliteDataArray) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& lhs, const ::android::hardware::gnss::V1_0::IGnssDebug::DebugData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IGnssDebug>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V1_0::IGnssDebug::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType, 3> hidl_enum_values<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType> = {
    ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::EPHEMERIS,
    ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::ALMANAC_ONLY,
    ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisType::NOT_AVAILABLE,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource, 4> hidl_enum_values<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource> = {
    ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::DEMODULATED,
    ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::SUPL_PROVIDED,
    ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::OTHER_SERVER_PROVIDED,
    ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisSource::OTHER,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth, 3> hidl_enum_values<::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth> = {
    ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::GOOD,
    ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::BAD,
    ::android::hardware::gnss::V1_0::IGnssDebug::SatelliteEphemerisHealth::UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSDEBUG_H
