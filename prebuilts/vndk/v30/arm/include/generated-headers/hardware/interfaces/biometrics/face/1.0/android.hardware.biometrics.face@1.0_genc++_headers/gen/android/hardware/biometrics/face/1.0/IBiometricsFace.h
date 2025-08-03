#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_IBIOMETRICSFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_IBIOMETRICSFACE_H

#include <android/hardware/biometrics/face/1.0/IBiometricsFaceClientCallback.h>
#include <android/hardware/biometrics/face/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace face {
namespace V1_0 {

/**
 * The HAL interface for biometric face authentication.
 */
struct IBiometricsFace : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.biometrics.face@1.0::IBiometricsFace"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for setCallback
     */
    using setCallback_cb = std::function<void(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& result)>;
    /**
     * Sets the current client callback.
     *
     * Registers a user function that must receive notifications from the HAL.
     * There is usually only one client (FaceService). This call must block
     * if the HAL state machine is in busy state until the HAL leaves the
     * busy state.
     *
     * All callback methods pass a deviceId to differentiate callback
     * invocations in the case where multiple sensors exist.
     *
     * @param clientCallback The client defined callback to register.
     * @return result, with its "value" parameter representing a "deviceId",
     *     which must be unique for a given sensor.
     */
    virtual ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::biometrics::face::V1_0::IBiometricsFaceClientCallback>& clientCallback, setCallback_cb _hidl_cb) = 0;

    /**
     * Sets the active user, which all subsequent HAL operations are applied to.
     *
     * HAL service implementors must ensure that operations are restricted to
     * the given user. Clients must not call any part of this interface, except
     * for setCallback(), without first having set an active user. The
     * implementation is responsible for cancelling the current operation and
     * returning to the idle state. Calling this method with the same userId
     * should have no effect on the state machine.
     *
     * Note that onLockoutChanged() MUST be invoked by the implementation in
     * response to a user change in order to update the framework with the
     * timeout of the new user (or 0 if the user is not locked out).
     *
     * @param userId A non-negative user identifier that must be unique and
     *     persistent for a given user.
     * @param storePath absolute filesystem path to the template storage
     *     directory. This must be the /data/vendor_de/<user>/facedata
     *     directory specified by the SeLinux policy.
     */
    virtual ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> setActiveUser(int32_t userId, const ::android::hardware::hidl_string& storePath) = 0;

    /**
     * Return callback for generateChallenge
     */
    using generateChallenge_cb = std::function<void(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& result)>;
    /**
     * Begins a secure transaction request, e.g. enroll() or resetLockout().
     *
     * Generates a unique and cryptographically secure random token used to
     * indicate the start of a secure transaction. generateChallenge() and
     * revokeChallenge() specify a window where the resulting HAT that is
     * generated in response to checking the user's PIN/pattern/password
     * can be used to verify/perform a secure transaction.
     *
     * generateChallenge() generates a challenge which must then be wrapped by
     * gatekeeper after verifying a successful strong authentication attempt,
     * which generates a Hardware Authentication Token. The challenge prevents
     * spoofing and replay attacks and ensures that only a transaction backed
     * by a user authentication (PIN/pattern/password) can proceed.
     *
     * The implementation should be tolerant of revokeChallenge() being invoked
     * after timeout has expired.
     *
     * @param challengeTimeoutSec A timeout in seconds, after which the driver
     *     must invalidate the challenge. This is to prevent bugs or crashes in
     *     the system from leaving a challenge enabled indefinitely.
     * @return result, with its "value" parameter representing a "challenge": a
     *     unique and cryptographically secure random token.
     */
    virtual ::android::hardware::Return<void> generateChallenge(uint32_t challengeTimeoutSec, generateChallenge_cb _hidl_cb) = 0;

    /**
     * Enrolls a user's face.
     *
     * Note that the Hardware Authentication Token must be valid for the
     * duration of enrollment and thus should be explicitly invalidated by a
     * call to revokeChallenge() when enrollment is complete, to reduce the
     * window of opportunity to re-use the challenge and HAT. For example,
     * Settings calls generateChallenge() once to allow the user to enroll one
     * or more faces or toggle secure settings without having to re-enter the
     * PIN/pattern/password. Once the user completes the operation, Settings
     * invokes revokeChallenge() to close the transaction. If the HAT is expired,
     * the implementation must invoke onError with UNABLE_TO_PROCESS.
     *
     * This method triggers the IBiometricsFaceClientCallback#onEnrollResult()
     * method.
     *
     * @param hat A valid Hardware Authentication Token, generated as a result
     *     of a generateChallenge() challenge being wrapped by the gatekeeper
     *     after a successful strong authentication request.
     * @param timeoutSec A timeout in seconds, after which this enroll
     *     attempt is cancelled. Note that the framework can continue
     *     enrollment by calling this again with a valid HAT. This timeout is
     *     expected to be used to limit power usage if the device becomes idle
     *     during enrollment. The implementation is expected to send
     *     ERROR_TIMEOUT if this happens.
     * @param disabledFeatures A list of features to be disabled during
     *     enrollment. Note that all features are enabled by default.
     * @return status The status of this method call.
     */
    virtual ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> enroll(const ::android::hardware::hidl_vec<uint8_t>& hat, uint32_t timeoutSec, const ::android::hardware::hidl_vec<::android::hardware::biometrics::face::V1_0::Feature>& disabledFeatures) = 0;

    /**
     * Finishes the secure transaction by invalidating the challenge generated
     * by generateChallenge().
     *
     * Clients must call this method once the secure transaction (e.g. enroll
     * or setFeature) is completed. See generateChallenge().
     *
     * @return status The status of this method call.
     */
    virtual ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> revokeChallenge() = 0;

    /**
     * Changes the state of previous enrollment setting. Because this may
     * decrease security, the user must enter their password before this method
     * is invoked (see @param HAT). The driver must verify the HAT before
     * changing any feature state. This method must return ILLEGAL_ARGUMENT if
     * the HAT or faceId is invalid. This must only be invoked after
     * setActiveUser() is called.
     *
     * Note: In some cases it may not be possible to change the state of this
     * flag without re-enrolling. For example, if the user didn't provide
     * attention during the original enrollment. This flag reflects the same
     * persistent state as the one passed to enroll().
     *
     * Note: This call may block for a short amount of time (few hundred
     * milliseconds). Clients are expected to invoke this asynchronously if it
     * takes much longer than the above limit. Also note that the result is
     * returned solely through Status (and not onError).
     *
     * @param feature The feature to be enabled or disabled.
     * @param enabled True to enable the feature, false to disable.
     * @param hat A valid Hardware Authentication Token, generated as a result
     *     of getChallenge().
     * @param faceId the ID of the enrollment returned by onEnrollResult() for
     *     the feature to update.
     * @return status The status of this method call.
     */
    virtual ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> setFeature(::android::hardware::biometrics::face::V1_0::Feature feature, bool enabled, const ::android::hardware::hidl_vec<uint8_t>& hat, uint32_t faceId) = 0;

    /**
     * Return callback for getFeature
     */
    using getFeature_cb = std::function<void(const ::android::hardware::biometrics::face::V1_0::OptionalBool& result)>;
    /**
     * Retrieves the current state of the feature. If the faceId is invalid,
     * the implementation must return ILLEGAL_ARGUMENT.
     *
     * @param faceId the ID of the enrollment returned by enroll().
     * @return result with the value set to true if the feature is enabled,
     *     false if disabled.
     */
    virtual ::android::hardware::Return<void> getFeature(::android::hardware::biometrics::face::V1_0::Feature feature, uint32_t faceId, getFeature_cb _hidl_cb) = 0;

    /**
     * Return callback for getAuthenticatorId
     */
    using getAuthenticatorId_cb = std::function<void(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& result)>;
    /**
     * Returns an identifier associated with the current face set.
     *
     * The authenticator ID must change whenever a new face is enrolled. The
     * authenticator ID must not be changed when a face is deleted. The
     * authenticator ID must be an entropy-encoded random number which all
     * current templates are tied to. The authenticator ID must be immutable
     * outside of an active enrollment window to prevent replay attacks.
     *
     * @return result, with its value parameter representing an
     *     "authenticatorId": an identifier associated to the user's current
     *     face enrollment.
     */
    virtual ::android::hardware::Return<void> getAuthenticatorId(getAuthenticatorId_cb _hidl_cb) = 0;

    /**
     * Cancels the current enroll, authenticate, remove, or enumerate operation.
     *
     * @return status The status of this method call.
     */
    virtual ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> cancel() = 0;

    /**
     * Enumerates all face templates associated with the active user.
     *
     * The onEnumerate() callback method is invoked once for each face template
     * found.
     *
     * @return status The status of this method call.
     */
    virtual ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> enumerate() = 0;

    /**
     * Removes a face template or all face templates associated with the active
     * user.
     *
     * This method triggers the IBiometricsFaceClientCallback#onRemoved() method.
     *
     * @param faceId The id correpsonding to the face to be removed; or 0 if all
     *    faces are to be removed.
     * @return status The status of this method call.
     */
    virtual ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> remove(uint32_t faceId) = 0;

    /**
     * Authenticates the active user.
     *
     * An optional operationId can be specified as a token from the transaction
     * being authorized. The hardware may enter a standby state during
     * authentication, where the device is idle to conserve power while
     * authenticating, e.g. after 3 seconds without finding a face. See
     * IBiometricsFace#userActivity() for more info.
     *
     * @param operationId A non-zero operation id associated with a crypto
     * object instance; or 0 if not being used.
     * @return status The status of this method call.
     */
    virtual ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> authenticate(uint64_t operationId) = 0;

    /**
     * A hint to the HAL to continue looking for faces.
     *
     * This method should only be used when the HAL is in the authenticating
     * or standby state. Using this method when the HAL is not in one of the
     * mentioned states must return OPERATION_NOT_SUPPORTED. Calling this
     * method while the HAL is already authenticating may extend the duration
     * where it's looking for a face.
     *
     * @return status The status of this method call.
     */
    virtual ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> userActivity() = 0;

    /**
     * Reset lockout for the current user.
     *
     * Note: This call may block for a short amount of time (few hundred
     * milliseconds). Clients are expected to invoke this asynchronously if it
     * takes much longer than the above limit.
     *
     * @param hat A valid Hardware Authentication Token, generated when the
     *     user authenticates with PIN/pattern/pass. When the Hardware
     *     Authentication Token is verified, lockout must be reset and
     *     onLockoutChanged must be called with duration 0.
     * @return status The status of this method call.
     */
    virtual ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> resetLockout(const ::android::hardware::hidl_vec<uint8_t>& hat) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::biometrics::face::V1_0::IBiometricsFace>> castFrom(const ::android::sp<::android::hardware::biometrics::face::V1_0::IBiometricsFace>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::biometrics::face::V1_0::IBiometricsFace>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IBiometricsFace> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IBiometricsFace> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IBiometricsFace> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IBiometricsFace> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IBiometricsFace> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IBiometricsFace> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IBiometricsFace> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IBiometricsFace> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::biometrics::face::V1_0::IBiometricsFace>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::biometrics::face::V1_0::IBiometricsFace>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::biometrics::face::V1_0::IBiometricsFace::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace face
}  // namespace biometrics
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_IBIOMETRICSFACE_H
