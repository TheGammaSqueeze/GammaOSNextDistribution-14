#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_1_IGNSS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_1_IGNSS_H

#include <android/hardware/gnss/1.0/IGnss.h>
#include <android/hardware/gnss/1.0/types.h>
#include <android/hardware/gnss/1.1/IGnssCallback.h>
#include <android/hardware/gnss/1.1/IGnssConfiguration.h>
#include <android/hardware/gnss/1.1/IGnssMeasurement.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_1 {

/**
 * Represents the standard GNSS (Global Navigation Satellite System) interface.
 */
struct IGnss : public ::android::hardware::gnss::V1_0::IGnss {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@1.1::IGnss"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Opens the interface and provides the callback routines to the implementation of this
     * interface.
     *
     * The framework calls this method to instruct the GPS engine to prepare for serving requests
     * from the framework. The GNSS HAL implementation must respond to all GNSS requests from the
     * framework upon successful return from this method until cleanup() method is called to
     * close this interface.
     *
     * @param callback Callback interface for IGnss.
     *
     * @return success Returns true on success.
     */
    virtual ::android::hardware::Return<bool> setCallback(const ::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>& callback) = 0;

    /**
     * Starts a location output stream using the IGnssCallback
     * gnssLocationCb(), following the settings from the most recent call to
     * setPositionMode().
     *
     * This output must operate independently of any GNSS location batching
     * operations, see the IGnssBatching.hal for details.
     *
     * @return success Returns true on success.
     */
    virtual ::android::hardware::Return<bool> start() = 0;

    /**
     * Stops the location output stream.
     *
     * @return success Returns true on success.
     */
    virtual ::android::hardware::Return<bool> stop() = 0;

    /**
     * Closes the interface.
     *
     * The cleanup() method is called by the framework to tell the GNSS HAL implementation to
     * not expect any GNSS requests in the immediate future - e.g. this may be called when
     * location is disabled by a user setting or low battery conditions. The GNSS HAL
     * implementation must immediately stop responding to any existing requests until the
     * setCallback() method is called again and the requests are re-initiated by the framework.
     *
     * After this method is called, the GNSS HAL implementation may choose to modify GNSS hardware
     * states to save power. It is expected that when setCallback() method is called again to
     * reopen this interface, to serve requests, there may be some minor delays in GNSS response
     * requests as hardware readiness states are restored, not to exceed those that occur on normal
     * device boot up.
     */
    virtual ::android::hardware::Return<void> cleanup() = 0;

    /**
     * Injects the current time.
     *
     * @param timeMs This is the UTC time received from the NTP server, its value
     * is given in milliseconds since January 1, 1970.
     * @param timeReferenceMs The corresponding value of
     * SystemClock.elapsedRealtime() from the device when the NTP response was
     * received in milliseconds.
     * @param uncertaintyMs Uncertainty associated with the value represented by
     * time. Represented in milliseconds.
     *
     * @return success Returns true if the operation is successful.
     */
    virtual ::android::hardware::Return<bool> injectTime(int64_t timeMs, int64_t timeReferenceMs, int32_t uncertaintyMs) = 0;

    /**
     * Injects current location from another location provider (typically cell
     * ID).
     *
     * @param latitudeDegrees Measured in Degrees.
     * @param longitudeDegrees Measured in Degrees.
     * @param accuracyMeters Measured in meters.
     *
     * @return success Returns true if successful.
     */
    virtual ::android::hardware::Return<bool> injectLocation(double latitudeDegrees, double longitudeDegrees, float accuracyMeters) = 0;

    /**
     * Specifies that the next call to start will not use the
     * information defined in the flags. GnssAidingData value of DELETE_ALL is
     * passed for a cold start.
     *
     * @param aidingDataFlags Flags specifying the aiding data to be deleted.
     */
    virtual ::android::hardware::Return<void> deleteAidingData(::android::hardware::gnss::V1_0::IGnss::GnssAidingData aidingDataFlags) = 0;

    /**
     * Sets the GnssPositionMode parameter,its associated recurrence value,
     * the time between fixes,requested fix accuracy and time to first fix.
     *
     * @param mode  Parameter must be one of MS_BASED or STANDALONE.
     * It is allowed by the platform (and it is recommended) to fallback to
     * MS_BASED if MS_ASSISTED is passed in, and MS_BASED is supported.
     * @recurrence GNSS position recurrence value, either periodic or single.
     * @param minIntervalMs Represents the time between fixes in milliseconds.
     * @param preferredAccuracyMeters Represents the requested fix accuracy in meters.
     * @param preferredTimeMs Represents the requested time to first fix in milliseconds.
     *
     * @return success Returns true if successful.
     */
    virtual ::android::hardware::Return<bool> setPositionMode(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode mode, ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence recurrence, uint32_t minIntervalMs, uint32_t preferredAccuracyMeters, uint32_t preferredTimeMs) = 0;

    /**
     * This method returns the IAGnssRil Interface.
     *
     * @return aGnssRilIface Handle to the IAGnssRil interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>> getExtensionAGnssRil() = 0;

    /**
     * This method returns the IGnssGeofencing Interface.
     *
     * @return gnssGeofencingIface Handle to the IGnssGeofencing interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssGeofencing>> getExtensionGnssGeofencing() = 0;

    /**
     * This method returns the IAGnss Interface.
     *
     * @return aGnssIface Handle to the IAGnss interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnss>> getExtensionAGnss() = 0;

    /**
     * This method returns the IGnssNi interface.
     *
     * @return gnssNiIface Handle to the IGnssNi interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssNi>> getExtensionGnssNi() = 0;

    /**
     * This method returns the IGnssMeasurement interface.
     *
     * @return gnssMeasurementIface Handle to the IGnssMeasurement interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssMeasurement>> getExtensionGnssMeasurement() = 0;

    /**
     * This method returns the IGnssNavigationMessage interface.
     *
     * @return gnssNavigationIface gnssNavigationIface to the IGnssNavigationMessage interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssNavigationMessage>> getExtensionGnssNavigationMessage() = 0;

    /**
     * This method returns the IGnssXtra interface.
     *
     * @return xtraIface Handle to the IGnssXtra interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssXtra>> getExtensionXtra() = 0;

    /**
     * This method returns the IGnssConfiguration interface.
     *
     * @return gnssConfigIface Handle to the IGnssConfiguration interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssConfiguration>> getExtensionGnssConfiguration() = 0;

    /**
     * This method returns the IGnssDebug interface.
     *
     * @return debugIface Handle to the IGnssDebug interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssDebug>> getExtensionGnssDebug() = 0;

    /**
     * This method returns the IGnssBatching interface.
     *
     * @return batchingIface Handle to the IGnssBatching interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssBatching>> getExtensionGnssBatching() = 0;

    /**
     * Opens the interface and provides the callback routines to the implementation of this
     * interface.
     *
     * The framework calls this method to instruct the GPS engine to prepare for serving requests
     * from the framework. The GNSS HAL implementation must respond to all GNSS requests from the
     * framework upon successful return from this method until cleanup() method is called to
     * close this interface.
     *
     * @param callback Callback interface for IGnss.
     *
     * @return success Returns true on success.
     */
    virtual ::android::hardware::Return<bool> setCallback_1_1(const ::android::sp<::android::hardware::gnss::V1_1::IGnssCallback>& callback) = 0;

    /**
     * Sets the GnssPositionMode parameter, its associated recurrence value,
     * the time between fixes, requested fix accuracy, time to first fix.
     *
     * @param mode Parameter must be one of MS_BASED or STANDALONE. It is allowed by the platform
     *     (and it is recommended) to fallback to MS_BASED if MS_ASSISTED is passed in, and MS_BASED
     *     is supported.
     * @param recurrence GNSS position recurrence value, either periodic or single.
     * @param minIntervalMs Represents the time between fixes in milliseconds.
     * @param preferredAccuracyMeters Represents the requested fix accuracy in meters.
     * @param preferredTimeMs Represents the requested time to first fix in milliseconds.
     * @param lowPowerMode When true, and IGnss.hal is the only client to the GNSS hardware, the
     *     GNSS hardware must make strong tradeoffs to substantially restrict power use.
     *     Specifically, in the case of a several second long minIntervalMs, the GNSS hardware must
     *     not, on average, run power hungry operations like RF and signal searches for more than
     *     one second per interval, and must make exactly one call to gnssSvStatusCb(), and either
     *     zero or one call to GnssLocationCb() at each interval. When false, HAL must operate in
     *     the nominal mode (similar to V1.0 where this flag wasn't present) and is expected to make
     *     power and performance tradoffs such as duty-cycling when signal conditions are good and
     *     more active searches to reacquire GNSS signals when no signals are present.
     *     When there are additional clients using the GNSS hardware other than IGnss.hal, the GNSS
     *     hardware may operate in a higher power mode, on behalf of those clients.
     *
     * @return success Returns true if successful.
     */
    virtual ::android::hardware::Return<bool> setPositionMode_1_1(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode mode, ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence recurrence, uint32_t minIntervalMs, uint32_t preferredAccuracyMeters, uint32_t preferredTimeMs, bool lowPowerMode) = 0;

    /**
     * This method returns the IGnssConfiguration interface.
     *
     * @return gnssConfigurationIface Handle to the IGnssConfiguration interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_1::IGnssConfiguration>> getExtensionGnssConfiguration_1_1() = 0;

    /**
     * This method returns the IGnssMeasurement interface.
     *
     * @return gnssMeasurementIface Handle to the IGnssMeasurement interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_1::IGnssMeasurement>> getExtensionGnssMeasurement_1_1() = 0;

    /**
     * Injects current location from the best available location provider.
     *
     * Unlike injectLocation, this method may inject a recent GNSS location from the HAL
     * implementation, if that is the best available location known to the framework.
     *
     * @param location Location information from the best available location provider.
     *
     * @return success Returns true if successful.
     */
    virtual ::android::hardware::Return<bool> injectBestLocation(const ::android::hardware::gnss::V1_0::GnssLocation& location) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_1::IGnss>> castFrom(const ::android::sp<::android::hardware::gnss::V1_1::IGnss>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_1::IGnss>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IGnss>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_1::IGnss>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IGnss> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnss> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnss> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnss> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnss> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnss> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnss> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnss> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_1::IGnss>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_1::IGnss>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V1_1::IGnss::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_1
}  // namespace gnss
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_1_IGNSS_H
