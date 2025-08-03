#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_IDRMPLUGIN_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_IDRMPLUGIN_H

#include <android/hardware/drm/1.0/IDrmPluginListener.h>
#include <android/hardware/drm/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {

/**
 * Ref: frameworks/native/include/media/drm/DrmAPI.h:DrmPlugin
 *
 * IDrmPlugin is used to interact with a specific drm plugin that was
 * created by IDrm::createPlugin. A drm plugin provides methods for
 * obtaining drm keys that may be used by a codec to decrypt protected
 * video content.
 */
struct IDrmPlugin : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.drm@1.0::IDrmPlugin"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for openSession
     */
    using openSession_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& sessionId)>;
    /**
     * Open a new session with the DrmPlugin object. A session ID is returned
     * in the sessionId parameter.
     * @return status the status of the call. The status must be OK or one of
     * the following errors: ERROR_DRM_NOT_PROVISIONED if the device requires
     * provisioning before it can open a session, ERROR_DRM_RESOURCE_BUSY if
     * there are insufficent resources available to open a session,
     * ERROR_DRM_CANNOT_HANDLE, if openSession is not supported at the time of
     * the call or ERROR_DRM_INVALID_STATE if the HAL is in a state where a
     * session cannot be opened.
     * @return sessionId the session ID for the newly opened session
     */
    virtual ::android::hardware::Return<void> openSession(openSession_cb _hidl_cb) = 0;

    /**
     * Close a session on the DrmPlugin object
     *
     * @param sessionId the session id the call applies to
     * @return status the status of the call.  The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, BAD_VALUE if the sessionId is invalid or ERROR_DRM_INVALID_STATE
     * if the HAL is in a state where the session cannot be closed.
     */
    virtual ::android::hardware::Return<::android::hardware::drm::V1_0::Status> closeSession(const ::android::hardware::hidl_vec<uint8_t>& sessionId) = 0;

    /**
     * Return callback for getKeyRequest
     */
    using getKeyRequest_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& request, ::android::hardware::drm::V1_0::KeyRequestType requestType, const ::android::hardware::hidl_string& defaultUrl)>;
    /**
     * A key request/response exchange occurs between the app and a License
     * Server to obtain the keys required to decrypt the content.
     * getKeyRequest() is used to obtain an opaque key request blob that is
     * delivered to the license server.
     *
     * @param scope may be a sessionId or a keySetId, depending on the
     * specified keyType. When the keyType is OFFLINE or STREAMING,
     * scope should be set to the sessionId the keys will be provided to.
     * When the keyType is RELEASE, scope should be set to the keySetId
     * of the keys being released.
     * @param initData container-specific data, its meaning is interpreted
     * based on the mime type provided in the mimeType parameter. It could
     * contain, for example, the content ID, key ID or other data obtained
     * from the content metadata that is required to generate the key request.
     * initData may be empty when keyType is RELEASE.
     * @param mimeType identifies the mime type of the content
     * @param keyType specifies if the keys are to be used for streaming,
     * offline or a release
     * @param optionalParameters included in the key request message to
     * allow a client application to provide additional message parameters to
     * the server.
     *
     * @return status the status of the call.  The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, ERROR_DRM_NOT_PROVISIONED if the device requires provisioning
     * before it can generate a key request, ERROR_DRM_CANNOT_HANDLE if
     * getKeyRequest is not supported at the time of the call, BAD_VALUE if any
     * parameters are invalid or ERROR_DRM_INVALID_STATE if the HAL is in a state
     * where a key request cannot be generated.
     * @return request if successful, the opaque key request blob is returned
     * @return requestType indicates type information about the returned
     * request. The type may be one of INITIAL, RENEWAL or RELEASE. An
     * INITIAL request is the first key request for a license. RENEWAL is a
     * subsequent key request used to refresh the keys in a license. RELEASE
     * corresponds to a keyType of RELEASE, which indicates keys are being
     * released.
     * @return defaultUrl the URL that the request may be sent to, if
     * provided by the drm HAL. The app may choose to override this
     * URL.
     */
    virtual ::android::hardware::Return<void> getKeyRequest(const ::android::hardware::hidl_vec<uint8_t>& scope, const ::android::hardware::hidl_vec<uint8_t>& initData, const ::android::hardware::hidl_string& mimeType, ::android::hardware::drm::V1_0::KeyType keyType, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::KeyValue>& optionalParameters, getKeyRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for provideKeyResponse
     */
    using provideKeyResponse_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& keySetId)>;
    /**
     * After a key response is received by the app, it is provided to the
     * Drm plugin using provideKeyResponse.
     *
     * @param scope may be a sessionId or a keySetId depending on the type
     * of the response. Scope should be set to the sessionId when the response
     * is for either streaming or offline key requests. Scope should be set to
     * the keySetId when the response is for a release request.
     * @param response the response from the key server that is being
     * provided to the drm HAL.
     *
     * @return status the status of the call.  The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, ERROR_DRM_NOT_PROVISIONED if the device requires provisioning
     * before it can handle the key response, ERROR_DRM_DEVICE_REVOKED if the
     * device has been disabled by the license policy, ERROR_DRM_CANNOT_HANDLE
     * if provideKeyResponse is not supported at the time of the call, BAD_VALUE
     * if any parameters are invalid or ERROR_DRM_INVALID_STATE if the HAL is
     * in a state where a key response cannot be handled.
     * @return keySetId when the response is for an offline key request, a
     * keySetId is returned in the keySetId vector parameter that can be used
     * to later restore the keys to a new session with the method restoreKeys.
     * When the response is for a streaming or release request, no keySetId is
     * returned.
     */
    virtual ::android::hardware::Return<void> provideKeyResponse(const ::android::hardware::hidl_vec<uint8_t>& scope, const ::android::hardware::hidl_vec<uint8_t>& response, provideKeyResponse_cb _hidl_cb) = 0;

    /**
     * Remove the current keys from a session
     *
     * @param sessionId the session id the call applies to
     * @return status the status of the call.  The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, BAD_VALUE if the sessionId is invalid or ERROR_DRM_INVALID_STATE
     * if the HAL is in a state where the keys cannot be removed.
     */
    virtual ::android::hardware::Return<::android::hardware::drm::V1_0::Status> removeKeys(const ::android::hardware::hidl_vec<uint8_t>& sessionId) = 0;

    /**
     * Restore persisted offline keys into a new session
     *
     * @param sessionId the session id the call applies to
     * @param keySetId identifies the keys to load, obtained from a prior
     * call to provideKeyResponse().
     * @return status the status of the call. The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, BAD_VALUE if any parameters are invalid or ERROR_DRM_INVALID_STATE
     * if the HAL is in a state where keys cannot be restored.
     */
    virtual ::android::hardware::Return<::android::hardware::drm::V1_0::Status> restoreKeys(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& keySetId) = 0;

    /**
     * Return callback for queryKeyStatus
     */
    using queryKeyStatus_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::KeyValue>& infoList)>;
    /**
     * Request an informative description of the license for the session. The
     * status is in the form of {name, value} pairs. Since DRM license policies
     * vary by vendor, the specific status field names are determined by each
     * DRM vendor. Refer to your DRM provider documentation for definitions of
     * the field names for a particular drm scheme.
     *
     * @param sessionId the session id the call applies to
     * @return status the status of the call. The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, BAD_VALUE if the sessionId is invalid or ERROR_DRM_INVALID_STATE
     * if the HAL is in a state where key status cannot be queried.
     * @return infoList a list of name value pairs describing the license
     */
    virtual ::android::hardware::Return<void> queryKeyStatus(const ::android::hardware::hidl_vec<uint8_t>& sessionId, queryKeyStatus_cb _hidl_cb) = 0;

    /**
     * Return callback for getProvisionRequest
     */
    using getProvisionRequest_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& request, const ::android::hardware::hidl_string& defaultUrl)>;
    /**
     * A provision request/response exchange occurs between the app and a
     * provisioning server to retrieve a device certificate. getProvisionRequest
     * is used to obtain an opaque provisioning request blob that is delivered
     * to the provisioning server.
     *
     * @param certificateType the type of certificate requested, e.g. "X.509"
     * @param certificateAuthority identifies the certificate authority. A
     * certificate authority (CA) is an entity which issues digital certificates
     * for use by other parties. It is an example of a trusted third party.
     * @return status the status of the call. The status must be OK or one of
     * the following errors: ERROR_DRM_CANNOT_HANDLE if the drm scheme does not
     * require provisioning or ERROR_DRM_INVALID_STATE if the HAL is in a state
     * where the provision request cannot be generated.
     * @return request if successful the opaque certificate request blob
     * is returned
     * @return defaultUrl URL that the provisioning request should be
     * sent to, if known by the HAL implementation.  If the HAL implementation
     * does not provide a defaultUrl, the returned string must be empty.
     */
    virtual ::android::hardware::Return<void> getProvisionRequest(const ::android::hardware::hidl_string& certificateType, const ::android::hardware::hidl_string& certificateAuthority, getProvisionRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for provideProvisionResponse
     */
    using provideProvisionResponse_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& certificate, const ::android::hardware::hidl_vec<uint8_t>& wrappedKey)>;
    /**
     * After a provision response is received by the app from a provisioning
     * server, it is provided to the Drm HAL using provideProvisionResponse.
     * The HAL implementation must receive the provision request and
     * store the provisioned credentials.
     *
     * @param response the opaque provisioning response received by the
     * app from a provisioning server.
     *
     * @return status the status of the call. The status must be OK or one of
     * the following errors: ERROR_DRM_DEVICE_REVOKED if the device has been
     * disabled by the license policy, BAD_VALUE if any parameters are invalid
     * or ERROR_DRM_INVALID_STATE if the HAL is in a state where the provision
     * response cannot be handled.
     * @return certificate the public certificate resulting from the provisioning
     * operation, if any. An empty vector indicates that no certificate was
     * returned.
     * @return wrappedKey an opaque object containing encrypted private key
     * material to be used by signRSA when computing an RSA signature on a
     * message, see the signRSA method.
     */
    virtual ::android::hardware::Return<void> provideProvisionResponse(const ::android::hardware::hidl_vec<uint8_t>& response, provideProvisionResponse_cb _hidl_cb) = 0;

    /**
     * Return callback for getSecureStops
     */
    using getSecureStops_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::SecureStop>& secureStops)>;
    /**
     * SecureStop is a way of enforcing the concurrent stream limit per
     * subscriber. It can securely monitor the lifetime of sessions across
     * device reboots by periodically persisting the session lifetime
     * status in secure storage.
     *
     * A signed version of the sessionID is written to persistent storage on the
     * device when each MediaCrypto object is created and periodically during
     * playback. The sessionID is signed by the device private key to prevent
     * tampering.
     *
     * When playback is completed the session is destroyed, and the secure
     * stops are queried by the app. The app then delivers the secure stop
     * message to a server which verifies the signature to confirm that the
     * session and its keys have been removed from the device. The persisted
     * record on the device is removed after receiving and verifying the
     * signed response from the server.
     *
     *
     * Get all secure stops on the device
     *
     * @return status the status of the call. The status must be OK or
     * ERROR_DRM_INVALID_STATE if the HAL is in a state where the secure stops
     * cannot be returned.
     * @return secureStops a list of the secure stop opaque objects
     */
    virtual ::android::hardware::Return<void> getSecureStops(getSecureStops_cb _hidl_cb) = 0;

    /**
     * Return callback for getSecureStop
     */
    using getSecureStop_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::drm::V1_0::SecureStop& secureStop)>;
    /**
     * Get all secure stops by secure stop ID
     *
     * @param secureStopId the ID of the secure stop to return. The
     * secure stop ID is delivered by the key server as part of the key
     * response and must also be known by the app.
     *
     * @return status the status of the call. The status must be OK or one of
     * the following errors: BAD_VALUE if the secureStopId is invalid or
     * ERROR_DRM_INVALID_STATE if the HAL is in a state where the secure stop
     * cannot be returned.
     * @return secureStop the secure stop opaque object
     */
    virtual ::android::hardware::Return<void> getSecureStop(const ::android::hardware::hidl_vec<uint8_t>& secureStopId, getSecureStop_cb _hidl_cb) = 0;

    /**
     * Release all secure stops on the device
     *
     * @return status the status of the call. The status must be OK or
     * ERROR_DRM_INVALID_STATE if the HAL is in a state where the secure
     * stops cannot be released.
     */
    virtual ::android::hardware::Return<::android::hardware::drm::V1_0::Status> releaseAllSecureStops() = 0;

    /**
     * Release a secure stop by secure stop ID
     *
     * @param secureStopId the ID of the secure stop to release. The
     * secure stop ID is delivered by the key server as part of the key
     * response and must also be known by the app.
     *
     * @return status the status of the call. The status must be OK or one of
     * the following errors: BAD_VALUE if the secureStopId is invalid or
     * ERROR_DRM_INVALID_STATE if the HAL is in a state where the secure stop
     * cannot be released.
     */
    virtual ::android::hardware::Return<::android::hardware::drm::V1_0::Status> releaseSecureStop(const ::android::hardware::hidl_vec<uint8_t>& secureStopId) = 0;

    /**
     * Return callback for getPropertyString
     */
    using getPropertyString_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_string& value)>;
    /**
     * A drm scheme can have properties that are settable and readable
     * by an app. There are a few forms of property access methods,
     * depending on the data type of the property.
     *
     * Property values defined by the public API are:
     *   "vendor" [string] identifies the maker of the drm scheme
     *   "version" [string] identifies the version of the drm scheme
     *   "description" [string] describes the drm scheme
     *   'deviceUniqueId' [byte array] The device unique identifier is
     *   established during device provisioning and provides a means of
     *   uniquely identifying each device.
     *
     * Since drm scheme properties may vary, additional field names may be
     * defined by each DRM vendor. Refer to your DRM provider documentation
     * for definitions of its additional field names.
     *
     *
     * Read a string property value given the property name.
     *
     * @param propertyName the name of the property
     * @return status the status of the call. The status must be OK or one of
     * the following errors: BAD_VALUE if the property name is invalid,
     * ERROR_DRM_CANNOT_HANDLE if the property is not supported, or
     * ERROR_DRM_INVALID_STATE if the HAL is in a state where the property
     * cannot be obtained.
     * @return value the property value string
     */
    virtual ::android::hardware::Return<void> getPropertyString(const ::android::hardware::hidl_string& propertyName, getPropertyString_cb _hidl_cb) = 0;

    /**
     * Return callback for getPropertyByteArray
     */
    using getPropertyByteArray_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& value)>;
    /**
     * Read a byte array property value given the property name.
     *
     * @param propertyName the name of the property
     * @return status the status of the call. The status must be OK or one of
     * the following errors: BAD_VALUE if the property name is invalid,
     * ERROR_DRM_CANNOT_HANDLE if the property is not supported, or
     * ERROR_DRM_INVALID_STATE if the HAL is in a state where the property
     * cannot be obtained.
     * @return value the property value byte array
     */
    virtual ::android::hardware::Return<void> getPropertyByteArray(const ::android::hardware::hidl_string& propertyName, getPropertyByteArray_cb _hidl_cb) = 0;

    /**
     * Write a property string value given the property name
     *
     * @param propertyName the name of the property
     * @param value the value to write
     * @return status the status of the call. The status must be OK or one of
     * the following errors: BAD_VALUE if the property name is invalid,
     * ERROR_DRM_CANNOT_HANDLE if the property is not supported, or
     * ERROR_DRM_INVALID_STATE if the HAL is in a state where the property
     * cannot be set.
     */
    virtual ::android::hardware::Return<::android::hardware::drm::V1_0::Status> setPropertyString(const ::android::hardware::hidl_string& propertyName, const ::android::hardware::hidl_string& value) = 0;

    /**
     * Write a property byte array value given the property name
     *
     * @param propertyName the name of the property
     * @param value the value to write
     * @return status the status of the call. The status must be OK or one of
     * the following errors: BAD_VALUE if the property name is invalid,
     * ERROR_DRM_CANNOT_HANDLE if the property is not supported, or
     * ERROR_DRM_INVALID_STATE if the HAL is in a state where the property
     * cannot be set.
     */
    virtual ::android::hardware::Return<::android::hardware::drm::V1_0::Status> setPropertyByteArray(const ::android::hardware::hidl_string& propertyName, const ::android::hardware::hidl_vec<uint8_t>& value) = 0;

    /**
     * The following methods implement operations on a CryptoSession to support
     * encrypt, decrypt, sign verify operations on operator-provided
     * session keys.
     *
     *
     * Set the cipher algorithm to be used for the specified session.
     *
     * @param sessionId the session id the call applies to
     * @param algorithm the algorithm to use. The string conforms to JCA
     * Standard Names for Cipher Transforms and is case insensitive. An
     * example algorithm is "AES/CBC/PKCS5Padding".
     * @return status the status of the call. The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, BAD_VALUE if any parameters are invalid or ERROR_DRM_INVALID_STATE
     * if the HAL is in a state where the algorithm cannot be set.
     */
    virtual ::android::hardware::Return<::android::hardware::drm::V1_0::Status> setCipherAlgorithm(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_string& algorithm) = 0;

    /**
     * Set the MAC algorithm to be used for computing hashes in a session.
     *
     * @param sessionId the session id the call applies to
     * @param algorithm the algorithm to use. The string conforms to JCA
     * Standard Names for Mac Algorithms and is case insensitive. An example MAC
     * algorithm string is "HmacSHA256".
     * @return status the status of the call. The status must be OK or one of the
     * following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, BAD_VALUE if any parameters are invalid or ERROR_DRM_INVALID_STATE
     * if the HAL is in a state where the algorithm cannot be set.
     */
    virtual ::android::hardware::Return<::android::hardware::drm::V1_0::Status> setMacAlgorithm(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_string& algorithm) = 0;

    /**
     * Return callback for encrypt
     */
    using encrypt_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& output)>;
    /**
     * Encrypt the provided input buffer with the cipher algorithm specified by
     * setCipherAlgorithm and the key selected by keyId, and return the
     * encrypted data.
     *
     * @param sessionId the session id the call applies to
     * @param keyId the ID of the key to use for encryption
     * @param input the input data to encrypt
     * @param iv the initialization vector to use for encryption
     * @return status the status of the call. The status must be OK or one of the
     * following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not opened,
     * BAD_VALUE if any parameters are invalid or ERROR_DRM_INVALID_STATE
     * if the HAL is in a state where the encrypt operation cannot be performed.
     * @return output the decrypted data
     */
    virtual ::android::hardware::Return<void> encrypt(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& keyId, const ::android::hardware::hidl_vec<uint8_t>& input, const ::android::hardware::hidl_vec<uint8_t>& iv, encrypt_cb _hidl_cb) = 0;

    /**
     * Return callback for decrypt
     */
    using decrypt_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& output)>;
    /**
     * Decrypt the provided input buffer with the cipher algorithm
     * specified by setCipherAlgorithm and the key selected by keyId,
     * and return the decrypted data.
     *
     * @param sessionId the session id the call applies to
     * @param keyId the ID of the key to use for decryption
     * @param input the input data to decrypt
     * @param iv the initialization vector to use for decryption
     * @return status the status of the call. The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, BAD_VALUE if any parameters are invalid or ERROR_DRM_INVALID_STATE
     * if the HAL is in a state where the decrypt operation cannot be
     * performed.
     * @return output the decrypted data
     */
    virtual ::android::hardware::Return<void> decrypt(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& keyId, const ::android::hardware::hidl_vec<uint8_t>& input, const ::android::hardware::hidl_vec<uint8_t>& iv, decrypt_cb _hidl_cb) = 0;

    /**
     * Return callback for sign
     */
    using sign_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& signature)>;
    /**
     * Compute a signature over the provided message using the mac algorithm
     * specified by setMacAlgorithm and the key selected by keyId and return
     * the signature.
     *
     * @param sessionId the session id the call applies to
     * @param keyId the ID of the key to use for decryption
     * @param message the message to compute a signature over
     * @return status the status of the call. The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, BAD_VALUE if any parameters are invalid or ERROR_DRM_INVALID_STATE
     * if the HAL is in a state where the sign operation cannot be
     * performed.
     * @return signature the computed signature
     */
    virtual ::android::hardware::Return<void> sign(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& keyId, const ::android::hardware::hidl_vec<uint8_t>& message, sign_cb _hidl_cb) = 0;

    /**
     * Return callback for verify
     */
    using verify_cb = std::function<void(::android::hardware::drm::V1_0::Status status, bool match)>;
    /**
     * Compute a hash of the provided message using the mac algorithm specified
     * by setMacAlgorithm and the key selected by keyId, and compare with the
     * expected result.
     *
     * @param sessionId the session id the call applies to
     * @param keyId the ID of the key to use for decryption
     * @param message the message to compute a hash of
     * @param signature the signature to verify
     * @return status the status of the call. The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is not
     * opened, BAD_VALUE if any parameters are invalid or ERROR_DRM_INVALID_STATE
     * if the HAL is in a state where the verify operation cannot be
     * performed.
     * @return match true if the signature is verified positively,
     * false otherwise.
     */
    virtual ::android::hardware::Return<void> verify(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& keyId, const ::android::hardware::hidl_vec<uint8_t>& message, const ::android::hardware::hidl_vec<uint8_t>& signature, verify_cb _hidl_cb) = 0;

    /**
     * Return callback for signRSA
     */
    using signRSA_cb = std::function<void(::android::hardware::drm::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& signature)>;
    /**
     * Compute an RSA signature on the provided message using the specified
     * algorithm.
     *
     * @param sessionId the session id the call applies to
     * @param algorithm the signing algorithm, such as "RSASSA-PSS-SHA1"
     * or "PKCS1-BlockType1"
     * @param message the message to compute the signature on
     * @param wrappedKey the private key returned during provisioning as
     * returned by provideProvisionResponse.
     * @return status the status of the call. The status must be OK or one of
     * the following errors: ERROR_DRM_SESSION_NOT_OPENED if the session is
     * not opened, BAD_VALUE if any parameters are invalid or
     * ERROR_DRM_INVALID_STATE if the HAL is in a state where the signRSA
     * operation cannot be performed.
     * @return signature the RSA signature computed over the message
     */
    virtual ::android::hardware::Return<void> signRSA(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_string& algorithm, const ::android::hardware::hidl_vec<uint8_t>& message, const ::android::hardware::hidl_vec<uint8_t>& wrappedkey, signRSA_cb _hidl_cb) = 0;

    /**
     * Plugins call the following methods to deliver events to the
     * java app.
     *
     *
     * Set a listener for a drm session. This allows the drm HAL to
     * make asynchronous calls back to the client of IDrm.
     *
     * @param listener instance of IDrmPluginListener to receive the events
     */
    virtual ::android::hardware::Return<void> setListener(const ::android::sp<::android::hardware::drm::V1_0::IDrmPluginListener>& listener) = 0;

    /**
     * Legacy event sending method, it sends events of various types using a
     * single overloaded set of parameters. This form is deprecated.
     *
     * @param eventType the type of the event
     * @param sessionId identifies the session the event originated from
     * @param data event-specific data blob
     */
    virtual ::android::hardware::Return<void> sendEvent(::android::hardware::drm::V1_0::EventType eventType, const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& data) = 0;

    /**
     * Send a license expiration update to the listener. The expiration
     * update indicates how long the current license is valid before it
     * needs to be renewed.
     *
     * @param sessionId identifies the session the event originated from
     * @param expiryTimeInMS the time when the keys need to be renewed.
     * The time is in milliseconds, relative to the Unix epoch. A time of 0
     * indicates that the keys never expire.
     */
    virtual ::android::hardware::Return<void> sendExpirationUpdate(const ::android::hardware::hidl_vec<uint8_t>& sessionId, int64_t expiryTimeInMS) = 0;

    /**
     * Send a keys change event to the listener. The keys change event
     * indicates the status of each key in the session. Keys can be
     * indicated as being usable, expired, outputnotallowed or statuspending.
     *
     * @param sessionId identifies the session the event originated from
     * @param keyStatusList indicates the status for each key ID in the
     * session.
     * @param hasNewUsableKey indicates if the event includes at least one
     * key that has become usable.
     */
    virtual ::android::hardware::Return<void> sendKeysChange(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::KeyStatus>& keyStatusList, bool hasNewUsableKey) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::drm::V1_0::IDrmPlugin>> castFrom(const ::android::sp<::android::hardware::drm::V1_0::IDrmPlugin>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::drm::V1_0::IDrmPlugin>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IDrmPlugin> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IDrmPlugin> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IDrmPlugin> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IDrmPlugin> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IDrmPlugin> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IDrmPlugin> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IDrmPlugin> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IDrmPlugin> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::drm::V1_0::IDrmPlugin>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::drm::V1_0::IDrmPlugin>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::drm::V1_0::IDrmPlugin::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace drm
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_IDRMPLUGIN_H
