#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_1_IGNSSCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_1_IGNSSCALLBACK_H

#include <android/hardware/gnss/1.0/IGnssCallback.h>

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
 * The interface is required for the HAL to communicate certain information
 * like status and location info back to the platform, the platform implements
 * the interfaces and passes a handle to the HAL.
 */
struct IGnssCallback : public ::android::hardware::gnss::V1_0::IGnssCallback {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@1.1::IGnssCallback"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Called when a GNSS location is available.
     *
     * @param location Location information from HAL.
     */
    virtual ::android::hardware::Return<void> gnssLocationCb(const ::android::hardware::gnss::V1_0::GnssLocation& location) = 0;

    /**
     * Called to communicate the status of the GNSS engine.
     *
     * @param status Status information from HAL.
     */
    virtual ::android::hardware::Return<void> gnssStatusCb(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue status) = 0;

    /**
     * @param svInfo SV status information from HAL.
     */
    virtual ::android::hardware::Return<void> gnssSvStatusCb(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& svInfo) = 0;

    /**
     * Called when NMEA data is available.
     * Callback for reporting NMEA sentences.
     *
     * @param timestamp Marks the instance of reporting.
     * @param nmea Follows standard NMEA 0183. Each sentence begins with a '$'
     * and ends with a carriage return/line feed sequence and can be no longer
     * than 80 characters of visible text (plus the line terminators). The data
     * is contained within this single line with data items separated by commas.
     * The data itself is just ascii text and may extend over multiple sentences
     * in certain specialized instances but is normally fully contained in one
     * variable length sentence. The data may vary in the amount of precision
     * contained in the message. For example time might be indicated to decimal
     * parts of a second or location may be shown with 3 or even 4 digits after
     * the decimal point. Programs that read the data must only use the commas
     * to determine the field boundaries and not depend on column positions.
     * There is a provision for a checksum at the end of each sentence which may
     * or may not be checked by the unit that reads the data. The checksum field
     * consists of a '*' and two hex digits representing an 8 bit exclusive OR
     * of all characters between, but not including, the '$' and '*'.
     */
    virtual ::android::hardware::Return<void> gnssNmeaCb(int64_t timestamp, const ::android::hardware::hidl_string& nmea) = 0;

    /**
     * Callback to inform framework of the GNSS engine's capabilities.
     *
     * @param capabilities Capability parameter is a bit field of
     * the Capabilities enum.
     */
    virtual ::android::hardware::Return<void> gnssSetCapabilitesCb(::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssCallback::Capabilities> capabilities) = 0;

    /**
     * Callback utility for acquiring the GNSS wakelock. This can be used to prevent
     * the CPU from suspending while handling GNSS events.
     */
    virtual ::android::hardware::Return<void> gnssAcquireWakelockCb() = 0;

    /**
     * Callback utility for releasing the GNSS wakelock.
     */
    virtual ::android::hardware::Return<void> gnssReleaseWakelockCb() = 0;

    /**
     * Callback for requesting NTP time
     */
    virtual ::android::hardware::Return<void> gnssRequestTimeCb() = 0;

    /**
     * Callback to inform framework of the engine's hardware version information.
     *
     * @param info GnssSystemInfo about the GPS/GNSS hardware.
     */
    virtual ::android::hardware::Return<void> gnssSetSystemInfoCb(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& info) = 0;

    /**
     * Callback to inform framework of the GNSS HAL implementation model & version name.
     *
     * This is a user-visible string that identifies the model and version of the GNSS HAL.
     * For example "ABC Co., Baseband Part 1234, RF Part 567, Software version 3.14.159"
     *
     * For privacy reasons, this string must not contain any device-specific serial number or other
     * identifier that uniquely identifies an individual device.
     *
     * This must be called in response to IGnss::setCallback
     *
     * @param name String providing the name of the GNSS HAL implementation
     */
    virtual ::android::hardware::Return<void> gnssNameCb(const ::android::hardware::hidl_string& name) = 0;

    /**
     * Callback for requesting Location.
     *
     * HAL implementation must call this when it wants the framework to provide locations to assist
     * with GNSS HAL operation, for example, to assist with time to first fix, error recovery, or to
     * supplement GNSS location for other clients of the GNSS HAL.
     *
     * If a request is made with independentFromGnss set to true, the framework must avoid
     * providing locations derived from GNSS locations (such as "fused" location), to help improve
     * information independence for situations such as error recovery.
     *
     * In response to this method call, GNSS HAL can expect zero, one, or more calls to
     * IGnss::injectLocation or IGnss::injectBestLocation, dependent on availability of location
     * from other sources, which may happen at some arbitrary delay. Generally speaking, HAL
     * implementations must be able to handle calls to IGnss::injectLocation or
     * IGnss::injectBestLocation at any time.
     *
     * @param independentFromGnss True if requesting a location that is independent from GNSS.
     */
    virtual ::android::hardware::Return<void> gnssRequestLocationCb(bool independentFromGnss) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_1::IGnssCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V1_1::IGnssCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_1::IGnssCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_1::IGnssCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IGnssCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnssCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssCallback> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_1::IGnssCallback>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_1::IGnssCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V1_1::IGnssCallback::descriptor;
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


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_1_IGNSSCALLBACK_H
