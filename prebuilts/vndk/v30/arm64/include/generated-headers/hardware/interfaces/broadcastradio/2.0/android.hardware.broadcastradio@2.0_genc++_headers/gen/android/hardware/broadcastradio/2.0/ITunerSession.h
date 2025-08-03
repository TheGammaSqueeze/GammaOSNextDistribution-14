#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_ITUNERSESSION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_ITUNERSESSION_H

#include <android/hardware/broadcastradio/2.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V2_0 {

struct ITunerSession : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.broadcastradio@2.0::ITunerSession"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Tune to a specified program.
     *
     * Automatically cancels pending tune(), scan() or step().
     * If the method returns OK, tuneFailed or currentProgramInfoChanged
     * callback must be called.
     *
     * @param program Program to tune to.
     * @return result OK if successfully started tuning.
     *                NOT_SUPPORTED if the program selector doesn't contain any
     *                supported identifier.
     *                INVALID_ARGUMENTS if the program selector contains
     *                identifiers in invalid format (i.e. out of range).
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result> tune(const ::android::hardware::broadcastradio::V2_0::ProgramSelector& program) = 0;

    /**
     * Tune (seek) to the next valid program on the "air".
     *
     * This might more naturally be called "seek" but for legacy reasons, the
     * entry point remains "scan". This should not be confused with the actual
     * scan operation (where the radio seeks through programs in a loop until
     * user chooses to stay on one of them) nor background scan operation (that
     * a tuner may do in order to locate all available programs.  This function
     * is meant to advance to the next detected program and stay there.
     *
     * Automatically cancels pending tune(), scan() or step().
     * If the method returns OK, tuneFailed or currentProgramInfoChanged
     * callback must be called.
     *
     * The skipSubChannel parameter is used to skip digital radio subchannels:
     *  - HD Radio SPS;
     *  - DAB secondary service.
     *
     * As an implementation detail, the HAL has the option to perform an actual
     * seek or select the next program from the list retrieved in the
     * background, if one is not stale.
     *
     * @param directionUp True to change towards higher numeric values
     *                    (frequency, channel number), false towards lower.
     * @param skipSubChannel Don't tune to subchannels.
     * @return result OK if the operation has successfully started.
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result> scan(bool directionUp, bool skipSubChannel) = 0;

    /**
     * Tune to the adjacent channel, which may not be occupied by any program.
     *
     * Automatically cancels pending tune(), scan() or step().
     * If the method returns OK, tuneFailed or currentProgramInfoChanged
     * callback must be called.
     *
     * @param directionUp True to change towards higher numeric values
     *                    (frequency, channel number), false towards lower.
     * @return result OK successfully started tuning.
     *                NOT_SUPPORTED if tuning to an unoccupied channel is not
     *                supported (i.e. for satellite radio).
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result> step(bool directionUp) = 0;

    /**
     * Cancel a pending tune(), scan() or step().
     *
     * If there is no such operation running, the call must be ignored.
     */
    virtual ::android::hardware::Return<void> cancel() = 0;

    /**
     * Applies a filter to the program list and starts sending program list
     * updates over onProgramListUpdated callback.
     *
     * There may be only one updates stream active at the moment. Calling this
     * method again must result in cancelling the previous update request.
     *
     * This call clears the program list on the client side, the HAL must send
     * the whole list again.
     *
     * If the program list scanning hardware (i.e. background tuner) is
     * unavailable at the moment, the call must succeed and start updates
     * when it becomes available.
     *
     * @param filter Filter to apply on the fetched program list.
     * @return result OK successfully started fetching list updates.
     *                NOT_SUPPORTED program list scanning is not supported
     *                by the hardware.
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result> startProgramListUpdates(const ::android::hardware::broadcastradio::V2_0::ProgramFilter& filter) = 0;

    /**
     * Stops sending program list updates.
     */
    virtual ::android::hardware::Return<void> stopProgramListUpdates() = 0;

    /**
     * Return callback for isConfigFlagSet
     */
    using isConfigFlagSet_cb = std::function<void(::android::hardware::broadcastradio::V2_0::Result result, bool value)>;
    /**
     * Fetches the current setting of a given config flag.
     *
     * The success/failure result must be consistent with setConfigFlag.
     *
     * @param flag Flag to fetch.
     * @return result OK successfully fetched the flag.
     *                INVALID_STATE if the flag is not applicable right now.
     *                NOT_SUPPORTED if the flag is not supported at all.
     * @return value The current value of the flag, if result is OK.
     */
    virtual ::android::hardware::Return<void> isConfigFlagSet(::android::hardware::broadcastradio::V2_0::ConfigFlag flag, isConfigFlagSet_cb _hidl_cb) = 0;

    /**
     * Sets the config flag.
     *
     * The success/failure result must be consistent with isConfigFlagSet.
     *
     * @param flag Flag to set.
     * @param value The new value of a given flag.
     * @return result OK successfully set the flag.
     *                INVALID_STATE if the flag is not applicable right now.
     *                NOT_SUPPORTED if the flag is not supported at all.
     */
    virtual ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result> setConfigFlag(::android::hardware::broadcastradio::V2_0::ConfigFlag flag, bool value) = 0;

    /**
     * Return callback for setParameters
     */
    using setParameters_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::VendorKeyValue>& results)>;
    /**
     * Generic method for setting vendor-specific parameter values.
     * The framework does not interpret the parameters, they are passed
     * in an opaque manner between a vendor application and HAL.
     *
     * Framework does not make any assumptions on the keys or values, other than
     * ones stated in VendorKeyValue documentation (a requirement of key
     * prefixes).
     *
     * For each pair in the result vector, the key must be one of the keys
     * contained in the input (possibly with wildcards expanded), and the value
     * must be a vendor-specific result status (i.e. the string "OK" or an error
     * code). The implementation may choose to return an empty vector, or only
     * return a status for a subset of the provided inputs, at its discretion.
     *
     * Application and HAL must not use keys with unknown prefix. In particular,
     * it must not place a key-value pair in results vector for unknown key from
     * parameters vector - instead, an unknown key should simply be ignored.
     * In other words, results vector may contain a subset of parameter keys
     * (however, the framework doesn't enforce a strict subset - the only
     * formal requirement is vendor domain prefix for keys).
     *
     * @param parameters Vendor-specific key-value pairs.
     * @return results Operation completion status for parameters being set.
     */
    virtual ::android::hardware::Return<void> setParameters(const ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::VendorKeyValue>& parameters, setParameters_cb _hidl_cb) = 0;

    /**
     * Return callback for getParameters
     */
    using getParameters_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::VendorKeyValue>& parameters)>;
    /**
     * Generic method for retrieving vendor-specific parameter values.
     * The framework does not interpret the parameters, they are passed
     * in an opaque manner between a vendor application and HAL.
     *
     * Framework does not cache set/get requests, so it's allowed for
     * getParameter to return a different value than previous setParameter call.
     *
     * The syntax and semantics of keys are up to the vendor (as long as prefix
     * rules are obeyed). For instance, vendors may include some form of
     * wildcard support. In such case, result vector may be of different size
     * than requested keys vector. However, wildcards are not recognized by
     * framework and they are passed as-is to the HAL implementation.
     *
     * Unknown keys must be ignored and not placed into results vector.
     *
     * @param keys Parameter keys to fetch.
     * @return parameters Vendor-specific key-value pairs.
     */
    virtual ::android::hardware::Return<void> getParameters(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& keys, getParameters_cb _hidl_cb) = 0;

    /**
     * Closes the session.
     *
     * The call must not fail and must only be issued once.
     *
     * After the close call is executed, no other calls to this interface
     * are allowed.
     */
    virtual ::android::hardware::Return<void> close() = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::broadcastradio::V2_0::ITunerSession>> castFrom(const ::android::sp<::android::hardware::broadcastradio::V2_0::ITunerSession>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::broadcastradio::V2_0::ITunerSession>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ITunerSession> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ITunerSession> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ITunerSession> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ITunerSession> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ITunerSession> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ITunerSession> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ITunerSession> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ITunerSession> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::broadcastradio::V2_0::ITunerSession>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::broadcastradio::V2_0::ITunerSession>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::broadcastradio::V2_0::ITunerSession::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_0
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_ITUNERSESSION_H
