#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_1_ICAS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_1_ICAS_H

#include <android/hardware/cas/1.0/ICas.h>
#include <android/hardware/cas/1.0/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace cas {
namespace V1_1 {

/**
 * ICas is the API to control the cas system and is accessible from both
 * Java and native level. It is used to manage sessions, provision/refresh
 * the cas system, and process the EMM/ECM messages. It also allows bi-directional,
 * scheme-specific communications between the client and the cas system.
 */
struct ICas : public ::android::hardware::cas::V1_0::ICas {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.cas@1.1::ICas"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Provide the CA private data from a CA_descriptor in the conditional
     * access table to a CasPlugin.
     *
     * @param pvtData a byte array containing the private data, the format of
     * which is scheme-specific and opaque to the framework.
     * @return status the status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::cas::V1_0::Status> setPrivateData(const ::android::hardware::hidl_vec<uint8_t>& pvtData) = 0;

    /**
     * Return callback for openSession
     */
    using openSession_cb = std::function<void(::android::hardware::cas::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& sessionId)>;
    /**
     * Open a session to descramble one or more streams scrambled by the
     * conditional access system.
     *
     * @return status the status of the call.
     * @return sessionId the id of the newly opened session.
     */
    virtual ::android::hardware::Return<void> openSession(openSession_cb _hidl_cb) = 0;

    /**
     * Close a session.
     *
     * @param sessionId the id of the session to be closed.
     * @return status the status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::cas::V1_0::Status> closeSession(const ::android::hardware::hidl_vec<uint8_t>& sessionId) = 0;

    /**
     * Provide the CA private data from a CA_descriptor in the program map
     * table to a session.
     *
     * @param sessionId the id of the session which the private data applies to.
     * @param pvtData a byte array containing the private data, the format of
     * which is scheme-specific and opaque to the framework.
     * @return status the status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::cas::V1_0::Status> setSessionPrivateData(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& pvtData) = 0;

    /**
     * Process an ECM from the ECM stream for this session’s elementary stream.
     *
     * @param sessionId the id of the session which the ecm data applies to.
     * @param ecm a byte array containing the ecm data.
     * @return status the status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::cas::V1_0::Status> processEcm(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& ecm) = 0;

    /**
     * Process an in-band EMM from the EMM stream.
     *
     * @param emm a byte array containing the emm data.
     * @return status the status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::cas::V1_0::Status> processEmm(const ::android::hardware::hidl_vec<uint8_t>& emm) = 0;

    /**
     * Send an scheme-specific event to the CasPlugin.
     *
     * @param event an integer denoting a scheme-specific event to be sent.
     * @param arg a scheme-specific integer argument for the event.
     * @param data a byte array containing scheme-specific data for the event.
     * @return status the status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::cas::V1_0::Status> sendEvent(int32_t event, int32_t arg, const ::android::hardware::hidl_vec<uint8_t>& eventData) = 0;

    /**
     * Initiate a provisioning operation for a CA system.
     *
     * @param provisionString string containing information needed for the
     * provisioning operation, the format of which is scheme and implementation
     * specific.
     * @return status the status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::cas::V1_0::Status> provision(const ::android::hardware::hidl_string& provisionString) = 0;

    /**
     * Notify the CA system to refresh entitlement keys.
     *
     * @param refreshType the type of the refreshment.
     * @param refreshData private data associated with the refreshment.
     * @return status the status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::cas::V1_0::Status> refreshEntitlements(int32_t refreshType, const ::android::hardware::hidl_vec<uint8_t>& refreshData) = 0;

    /**
     * Release the descrambler instance.
     *
     * @return status the status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::cas::V1_0::Status> release() = 0;

    /**
     * Send an scheme-specific session event to the CasPlugin.
     *
     * @param sessionId the id of an opened session.
     * @param event an integer denoting a scheme-specific event to be sent.
     * @param arg a scheme-specific integer argument for the event.
     * @param data a byte array containing scheme-specific data for the event.
     * @return status the status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::cas::V1_0::Status> sendSessionEvent(const ::android::hardware::hidl_vec<uint8_t>& sessionId, int32_t event, int32_t arg, const ::android::hardware::hidl_vec<uint8_t>& eventData) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::cas::V1_1::ICas>> castFrom(const ::android::sp<::android::hardware::cas::V1_1::ICas>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::cas::V1_1::ICas>> castFrom(const ::android::sp<::android::hardware::cas::V1_0::ICas>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::cas::V1_1::ICas>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ICas> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICas> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICas> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICas> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ICas> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICas> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICas> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICas> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::cas::V1_1::ICas>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::cas::V1_1::ICas>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::cas::V1_1::ICas::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_1
}  // namespace cas
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_1_ICAS_H
