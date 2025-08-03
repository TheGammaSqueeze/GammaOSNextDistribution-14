#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_IGATEKEEPER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_IGATEKEEPER_H

#include <android/hardware/gatekeeper/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gatekeeper {
namespace V1_0 {

struct IGatekeeper : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gatekeeper@1.0::IGatekeeper"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for enroll
     */
    using enroll_cb = std::function<void(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& response)>;
    /**
     * Enrolls desiredPassword, which may be derived from a user selected pin
     * or password, with the private key used only for enrolling authentication
     * factor data.
     *
     * If there was already a password enrolled, current password handle must be
     * passed in currentPasswordHandle, and current password must be passed in
     * currentPassword. Valid currentPassword must verify() against
     * currentPasswordHandle.
     *
     * @param uid The Android user identifier
     *
     * @param currentPasswordHandle The currently enrolled password handle the user
     *    wants to replace. May be empty only if there's no currently enrolled
     *    password. Otherwise must be non-empty.
     *
     * @param currentPassword The user's current password in plain text.
     *    it MUST verify against current_password_handle if the latter is not-empty
     *
     * @param desiredPassword The new password the user wishes to enroll in
     *    plaintext.
     *
     * @return response
     *    On success, data buffer must contain the new password handle referencing
     *    the password provided in desiredPassword.
     *    This buffer can be used on subsequent calls to enroll or
     *    verify. On error, this buffer must be empty.
     *    response.code must always contain operation completion status.
     *    This method may return ERROR_GENERAL_FAILURE or ERROR_RETRY_TIMEOUT on
     *    failure. It must return STATUS_OK on success.
     *    If ERROR_RETRY_TIMEOUT is returned, response.timeout must be non-zero.
     */
    virtual ::android::hardware::Return<void> enroll(uint32_t uid, const ::android::hardware::hidl_vec<uint8_t>& currentPasswordHandle, const ::android::hardware::hidl_vec<uint8_t>& currentPassword, const ::android::hardware::hidl_vec<uint8_t>& desiredPassword, enroll_cb _hidl_cb) = 0;

    /**
     * Return callback for verify
     */
    using verify_cb = std::function<void(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& response)>;
    /**
     * Verifies that providedPassword matches enrolledPasswordHandle.
     *
     * Implementations of this module may retain the result of this call
     * to attest to the recency of authentication.
     *
     * On success, returns verification token in response.data, which shall be
     * usable to attest password verification to other trusted services.
     *
     * @param uid The Android user identifier
     *
     * @param challenge An optional challenge to authenticate against, or 0.
     *    Used when a separate authenticator requests password verification,
     *    or for transactional password authentication.
     *
     * @param enrolledPasswordHandle The currently enrolled password handle that
     *    user wishes to verify against. Must be non-empty.
     *
     * @param providedPassword The plaintext password to be verified against the
     *    enrolledPasswordHandle
     *
     * @return response
     *    On success, a non-empty data buffer containing the
     *    authentication token resulting from this verification is returned.
     *    On error, data buffer must be empty.
     *    response.code must always contain operation completion status.
     *    This method may return ERROR_GENERAL_FAILURE or ERROR_RETRY_TIMEOUT on
     *    failure. It must return STATUS_OK on success.
     *    If password re-enrollment is necessary, it must return STATUS_REENROLL.
     *    If ERROR_RETRY_TIMEOUT is returned, response.timeout must be non-zero.
     */
    virtual ::android::hardware::Return<void> verify(uint32_t uid, uint64_t challenge, const ::android::hardware::hidl_vec<uint8_t>& enrolledPasswordHandle, const ::android::hardware::hidl_vec<uint8_t>& providedPassword, verify_cb _hidl_cb) = 0;

    /**
     * Return callback for deleteUser
     */
    using deleteUser_cb = std::function<void(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& response)>;
    /**
     * Deletes the enrolledPasswordHandle associated with the uid. Once deleted
     * the user cannot be verified anymore.
     * This is an optional method.
     *
     * @param uid The Android user identifier
     *
     * @return response
     *    response.code must always contain operation completion status.
     *    This method may return ERROR_GENERAL_FAILURE or ERROR_RETRY_TIMEOUT on
     *    failure. It must return STATUS_OK on success.
     *    If not implemented, it must return ERROR_NOT_IMPLEMENTED.
     *    If ERROR_RETRY_TIMEOUT is returned, response.timeout must be non-zero.
     */
    virtual ::android::hardware::Return<void> deleteUser(uint32_t uid, deleteUser_cb _hidl_cb) = 0;

    /**
     * Return callback for deleteAllUsers
     */
    using deleteAllUsers_cb = std::function<void(const ::android::hardware::gatekeeper::V1_0::GatekeeperResponse& response)>;
    /**
     * Deletes all the enrolled_password_handles for all uid's. Once called,
     * no users must be enrolled on the device.
     * This is an optional method.
     *
     * @return response
     *    response.code must always contain operation completion status.
     *    This method may return ERROR_GENERAL_FAILURE or ERROR_RETRY_TIMEOUT on
     *    failure. It must return STATUS_OK on success.
     *    If not implemented, it must return ERROR_NOT_IMPLEMENTED.
     *    If ERROR_RETRY_TIMEOUT is returned, response.timeout must be non-zero.
     */
    virtual ::android::hardware::Return<void> deleteAllUsers(deleteAllUsers_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gatekeeper::V1_0::IGatekeeper>> castFrom(const ::android::sp<::android::hardware::gatekeeper::V1_0::IGatekeeper>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gatekeeper::V1_0::IGatekeeper>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IGatekeeper> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGatekeeper> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGatekeeper> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGatekeeper> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGatekeeper> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGatekeeper> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGatekeeper> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGatekeeper> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::gatekeeper::V1_0::IGatekeeper>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::gatekeeper::V1_0::IGatekeeper>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gatekeeper::V1_0::IGatekeeper::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace gatekeeper
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_IGATEKEEPER_H
