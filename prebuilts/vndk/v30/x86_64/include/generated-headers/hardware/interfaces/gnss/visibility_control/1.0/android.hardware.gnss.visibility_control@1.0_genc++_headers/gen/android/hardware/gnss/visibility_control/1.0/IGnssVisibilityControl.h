#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_VISIBILITY_CONTROL_V1_0_IGNSSVISIBILITYCONTROL_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_VISIBILITY_CONTROL_V1_0_IGNSSVISIBILITYCONTROL_H

#include <android/hardware/gnss/visibility_control/1.0/IGnssVisibilityControlCallback.h>
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
namespace visibility_control {
namespace V1_0 {

/**
 * Represents the GNSS location reporting permissions and notification interface.
 *
 * This interface is used to tell the GNSS HAL implementation whether the framework user has
 * granted permission to the GNSS HAL implementation to provide GNSS location information for
 * non-framework (NFW), non-user initiated emergency use cases, and to notify the framework user
 * of these GNSS location information deliveries.
 *
 * For user initiated emergency cases (and for the configured extended emergency session duration),
 * the GNSS HAL implementation must serve the emergency location supporting network initiated
 * location requests immediately irrespective of this permission settings.
 *
 * There is no separate need for the GNSS HAL implementation to monitor the global device location
 * on/off setting. Permission to use GNSS for non-framework use cases is expressly controlled
 * by the method enableNfwLocationAccess(). The framework monitors the location permission settings
 * of the configured proxy applications(s), and device location settings, and calls the method
 * enableNfwLocationAccess() whenever the user control proxy applications have, or do not have,
 * location permission. The proxy applications are used to provide user visibility and control of
 * location access by the non-framework on/off device entities they are representing.
 *
 * For device user visibility, the GNSS HAL implementation must call the method
 * IGnssVisibilityControlCallback.nfwNotifyCb() whenever location request is rejected or
 * location information is provided to non-framework entities (on or off device). This includes
 * the network initiated location requests for user-initiated emergency use cases as well.
 *
 * The HAL implementations that support this interface must not report GNSS location, measurement,
 * status, or other information that can be used to derive user location to any entity when not
 * expressly authorized by this HAL. This includes all endpoints for location information
 * off the device, including carriers, vendors, OEM and others directly or indirectly.
 */
struct IGnssVisibilityControl : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss.visibility_control@1.0::IGnssVisibilityControl"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Enables/disables non-framework entity location access permission in the GNSS HAL.
     *
     * The framework will call this method to update GNSS HAL implementation every time the
     * framework user, through the given proxy application(s) and/or device location settings,
     * explicitly grants/revokes the location access permission for non-framework, non-user
     * initiated emergency use cases.
     *
     * Whenever the user location information is delivered to non-framework entities, the HAL
     * implementation must call the method IGnssVisibilityControlCallback.nfwNotifyCb() to notify
     * the framework for user visibility.
     *
     * @param proxyApps Full list of package names of proxy Android applications representing
     * the non-framework location access entities (on/off the device) for which the framework
     * user has granted non-framework location access permission. The GNSS HAL implementation
     * must provide location information only to non-framework entities represented by these
     * proxy applications.
     *
     * The package name of the proxy Android application follows the standard Java language
     * package naming format. For example, com.example.myapp.
     *
     * @return success True if the operation was successful.
     */
    virtual ::android::hardware::Return<bool> enableNfwLocationAccess(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& proxyApps) = 0;

    /**
     * Registers the callback for HAL implementation to use.
     *
     * @param callback Handle to IGnssVisibilityControlCallback interface.
     */
    virtual ::android::hardware::Return<bool> setCallback(const ::android::sp<::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControlCallback>& callback) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControl>> castFrom(const ::android::sp<::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControl>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControl>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IGnssVisibilityControl> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssVisibilityControl> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssVisibilityControl> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssVisibilityControl> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnssVisibilityControl> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssVisibilityControl> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssVisibilityControl> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssVisibilityControl> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControl>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControl>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControl::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace visibility_control
}  // namespace gnss
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_VISIBILITY_CONTROL_V1_0_IGNSSVISIBILITYCONTROL_H
