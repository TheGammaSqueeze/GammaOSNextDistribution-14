#ifndef HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace keymaster {
namespace V4_0 {

// Forward declaration for forward reference support:
enum class Constants : uint32_t;
enum class TagType : uint32_t;
enum class Tag : uint32_t;
enum class Algorithm : uint32_t;
enum class BlockMode : uint32_t;
enum class PaddingMode : uint32_t;
enum class Digest : uint32_t;
enum class EcCurve : uint32_t;
enum class KeyOrigin : uint32_t;
enum class KeyBlobUsageRequirements : uint32_t;
enum class KeyPurpose : uint32_t;
enum class ErrorCode : int32_t;
enum class KeyDerivationFunction : uint32_t;
enum class HardwareAuthenticatorType : uint32_t;
enum class SecurityLevel : uint32_t;
enum class KeyFormat : uint32_t;
struct KeyParameter;
struct KeyCharacteristics;
struct HardwareAuthToken;
struct HmacSharingParameters;
struct VerificationToken;

/**
 * Time in milliseconds since some arbitrary point in time.  Time must be monotonically increasing,
 * and a secure environment's notion of "current time" must not repeat until the Android device
 * reboots, or until at least 50 million years have elapsed (note that this requirement is satisfied
 * by setting the clock to zero during each boot, and then counting time accurately).
 */
typedef uint64_t Timestamp;

/**
 * A place to define any needed constants.
 */
enum class Constants : uint32_t {
    AUTH_TOKEN_MAC_LENGTH = 32u,
};

enum class TagType : uint32_t {
    /**
     * Invalid type, used to designate a tag as uninitialized.
     */
    INVALID = 0u /* 0 << 28 */,
    /**
     * Enumeration value.
     */
    ENUM = 268435456u /* 1 << 28 */,
    /**
     * Repeatable enumeration value.
     */
    ENUM_REP = 536870912u /* 2 << 28 */,
    /**
     * 32-bit unsigned integer.
     */
    UINT = 805306368u /* 3 << 28 */,
    /**
     * Repeatable 32-bit unsigned integer.
     */
    UINT_REP = 1073741824u /* 4 << 28 */,
    /**
     * 64-bit unsigned integer.
     */
    ULONG = 1342177280u /* 5 << 28 */,
    /**
     * 64-bit unsigned integer representing a date and time, in milliseconds since 1 Jan 1970.
     */
    DATE = 1610612736u /* 6 << 28 */,
    /**
     * Boolean.  If a tag with this type is present, the value is "true".  If absent, "false".
     */
    BOOL = 1879048192u /* 7 << 28 */,
    /**
     * Byte string containing an arbitrary-length integer, big-endian ordering.
     */
    BIGNUM = 2147483648u /* 8 << 28 */,
    /**
     * Byte string
     */
    BYTES = 2415919104u /* 9 << 28 */,
    /**
     * Repeatable 64-bit unsigned integer
     */
    ULONG_REP = 2684354560u /* 10 << 28 */,
};

enum class Tag : uint32_t {
    INVALID = 0u /* TagType:INVALID | 0 */,
    /**
     * Tag::PURPOSE specifies the set of purposes for which the key may be used.  Possible values
     * are defined in the KeyPurpose enumeration.
     *
     * This tag is repeatable; keys may be generated with multiple values, although an operation has
     * a single purpose.  When begin() is called to start an operation, the purpose of the operation
     * is specified.  If the purpose specified for the operation is not authorized by the key (the
     * key didn't have a corresponding Tag::PURPOSE provided during generation/import), the
     * operation must fail with ErrorCode::INCOMPATIBLE_PURPOSE.
     *
     * Must be hardware-enforced.
     */
    PURPOSE = 536870913u /* TagType:ENUM_REP | 1 */,
    /**
     * Tag::ALGORITHM specifies the cryptographic algorithm with which the key is used.  This tag
     * must be provided to generateKey and importKey, and must be specified in the wrapped key
     * provided to importWrappedKey.
     *
     * Must be hardware-enforced.
     */
    ALGORITHM = 268435458u /* TagType:ENUM | 2 */,
    /**
     * Tag::KEY_SIZE pecifies the size, in bits, of the key, measuring in the normal way for the
     * key's algorithm.  For example, for RSA keys, Tag::KEY_SIZE specifies the size of the public
     * modulus.  For AES keys it specifies the length of the secret key material.  For 3DES keys it
     * specifies the length of the key material, not counting parity bits (though parity bits must
     * be provided for import, etc.).  Since only three-key 3DES keys are supported, 3DES
     * Tag::KEY_SIZE must be 168.
     *
     * Must be hardware-enforced.
     */
    KEY_SIZE = 805306371u /* TagType:UINT | 3 */,
    /**
     * Tag::BLOCK_MODE specifies the block cipher mode(s) with which the key may be used.  This tag
     * is only relevant to AES and 3DES keys.  Possible values are defined by the BlockMode enum.
     *
     * This tag is repeatable for key generation/import.  For AES and 3DES operations the caller
     * must specify a Tag::BLOCK_MODE in the additionalParams argument of begin().  If the mode is
     * missing or the specified mode is not in the modes specified for the key during
     * generation/import, the operation must fail with ErrorCode::INCOMPATIBLE_BLOCK_MODE.
     *
     * Must be hardware-enforced.
     */
    BLOCK_MODE = 536870916u /* TagType:ENUM_REP | 4 */,
    /*
     * BlockMode.
     *
     *
     * Tag::DIGEST specifies the digest algorithms that may be used with the key to perform signing
     * and verification operations.  This tag is relevant to RSA, ECDSA and HMAC keys.  Possible
     * values are defined by the Digest enum.
     *
     * This tag is repeatable for key generation/import.  For signing and verification operations,
     * the caller must specify a digest in the additionalParams argument of begin().  If the digest
     * is missing or the specified digest is not in the digests associated with the key, the
     * operation must fail with ErrorCode::INCOMPATIBLE_DIGEST.
     *
     * Must be hardware-enforced.
     */
    DIGEST = 536870917u /* TagType:ENUM_REP | 5 */,
    /**
     * Tag::PADDING specifies the padding modes that may be used with the key.  This tag is relevant
     * to RSA, AES and 3DES keys.  Possible values are defined by the PaddingMode enum.
     *
     * PaddingMode::RSA_OAEP and PaddingMode::RSA_PKCS1_1_5_ENCRYPT are used only for RSA
     * encryption/decryption keys and specify RSA OAEP padding and RSA PKCS#1 v1.5 randomized
     * padding, respectively.  PaddingMode::RSA_PSS and PaddingMode::RSA_PKCS1_1_5_SIGN are used
     * only for RSA signing/verification keys and specify RSA PSS padding and RSA PKCS#1 v1.5
     * deterministic padding, respectively.
     *
     * PaddingMode::NONE may be used with either RSA, AES or 3DES keys.  For AES or 3DES keys, if
     * PaddingMode::NONE is used with block mode ECB or CBC and the data to be encrypted or
     * decrypted is not a multiple of the AES block size in length, the call to finish() must fail
     * with ErrorCode::INVALID_INPUT_LENGTH.
     *
     * PaddingMode::PKCS7 may only be used with AES and 3DES keys, and only with ECB and CBC modes.
     *
     * In any case, if the caller specifies a padding mode that is not usable with the key's
     * algorithm, the generation or import method must return ErrorCode::INCOMPATIBLE_PADDING_MODE.
     *
     * This tag is repeatable.  A padding mode must be specified in the call to begin().  If the
     * specified mode is not authorized for the key, the operation must fail with
     * ErrorCode::INCOMPATIBLE_BLOCK_MODE.
     *
     * Must be hardware-enforced.
     */
    PADDING = 536870918u /* TagType:ENUM_REP | 6 */,
    /**
     * Tag::CALLER_NONCE specifies that the caller can provide a nonce for nonce-requiring
     * operations.  This tag is boolean, so the possible values are true (if the tag is present) and
     * false (if the tag is not present).
     *
     * This tag is used only for AES and 3DES keys, and is only relevant for CBC, CTR and GCM block
     * modes.  If the tag is not present in a key's authorization list, implementations must reject
     * any operation that provides Tag::NONCE to begin() with ErrorCode::CALLER_NONCE_PROHIBITED.
     *
     * Must be hardware-enforced.
     */
    CALLER_NONCE = 1879048199u /* TagType:BOOL | 7 */,
    /**
     * Tag::MIN_MAC_LENGTH specifies the minimum length of MAC that can be requested or verified
     * with this key for HMAC keys and AES keys that support GCM mode.
     *
     * This value is the minimum MAC length, in bits.  It must be a multiple of 8 bits.  For HMAC
     * keys, the value must be least 64 and no more than 512.  For GCM keys, the value must be at
     * least 96 and no more than 128.  If the provided value violates these requirements,
     * generateKey() or importKey() must return ErrorCode::UNSUPPORTED_KEY_SIZE.
     *
     * Must be hardware-enforced.
     */
    MIN_MAC_LENGTH = 805306376u /* TagType:UINT | 8 */,
    /**
     * Tag::EC_CURVE specifies the elliptic curve.  EC key generation requests may have
     * Tag:EC_CURVE, Tag::KEY_SIZE, or both.  If both are provided and the size and curve do not
     * match, IKeymasterDevice must return ErrorCode::INVALID_ARGUMENT.
     *
     * Must be hardware-enforced.
     */
    EC_CURVE = 268435466u /* TagType:ENUM | 10 */,
    /**
     * Tag::RSA_PUBLIC_EXPONENT specifies the value of the public exponent for an RSA key pair.
     * This tag is relevant only to RSA keys, and is required for all RSA keys.
     *
     * The value is a 64-bit unsigned integer that satisfies the requirements of an RSA public
     * exponent.  This value must be a prime number.  IKeymasterDevice implementations must support
     * the value 2^16+1 and may support other reasonable values.  If no exponent is specified or if
     * the specified exponent is not supported, key generation must fail with
     * ErrorCode::INVALID_ARGUMENT.
     *
     * Must be hardware-enforced.
     */
    RSA_PUBLIC_EXPONENT = 1342177480u /* TagType:ULONG | 200 */,
    /**
     * Tag::INCLUDE_UNIQUE_ID is specified during key generation to indicate that an attestation
     * certificate for the generated key should contain an application-scoped and time-bounded
     * device-unique ID.  See Tag::UNIQUE_ID.
     *
     * Must be hardware-enforced.
     */
    INCLUDE_UNIQUE_ID = 1879048394u /* TagType:BOOL | 202 */,
    /**
     * Tag::BLOB_USAGE_REQUIREMENTS specifies the necessary system environment conditions for the
     * generated key to be used.  Possible values are defined by the KeyBlobUsageRequirements enum.
     *
     * This tag is specified by the caller during key generation or import to require that the key
     * is usable in the specified condition.  If the caller specifies Tag::BLOB_USAGE_REQUIREMENTS
     * with value KeyBlobUsageRequirements::STANDALONE the IKeymasterDevice must return a key blob
     * that can be used without file system support.  This is critical for devices with encrypted
     * disks, where the file system may not be available until after a Keymaster key is used to
     * decrypt the disk.
     *
     * Must be hardware-enforced.
     */
    BLOB_USAGE_REQUIREMENTS = 268435757u /* TagType:ENUM | 301 */,
    /**
     * Tag::BOOTLOADER_ONLY specifies only the bootloader can use the key.
     *
     * Any attempt to use a key with Tag::BOOTLOADER_ONLY from the Android system must fail with
     * ErrorCode::INVALID_KEY_BLOB.
     *
     * Must be hardware-enforced.
     */
    BOOTLOADER_ONLY = 1879048494u /* TagType:BOOL | 302 */,
    /**
     * Tag::ROLLBACK_RESISTANCE specifies that the key has rollback resistance, meaning that when
     * deleted with deleteKey() or deleteAllKeys(), the key is guaranteed to be permanently deleted
     * and unusable.  It's possible that keys without this tag could be deleted and then restored
     * from backup.
     *
     * This tag is specified by the caller during key generation or import to require.  If the
     * IKeymasterDevice cannot guarantee rollback resistance for the specified key, it must return
     * ErrorCode::ROLLBACK_RESISTANCE_UNAVAILABLE.  IKeymasterDevice implementations are not
     * required to support rollback resistance.
     *
     * Must be hardwared-enforced.
     */
    ROLLBACK_RESISTANCE = 1879048495u /* TagType:BOOL | 303 */,
    HARDWARE_TYPE = 268435760u /* TagType:ENUM | 304 */,
    /**
     * Tag::ACTIVE_DATETIME specifies the date and time at which the key becomes active, in
     * milliseconds since Jan 1, 1970.  If a key with this tag is used prior to the specified date
     * and time, IKeymasterDevice::begin() must return ErrorCode::KEY_NOT_YET_VALID;
     *
     * Need not be hardware-enforced.
     */
    ACTIVE_DATETIME = 1610613136u /* TagType:DATE | 400 */,
    /*
     * Start of validity.
     *
     *
     * Tag::ORIGINATION_EXPIRE_DATETIME specifies the date and time at which the key expires for
     * signing and encryption purposes.  After this time, any attempt to use a key with
     * KeyPurpose::SIGN or KeyPurpose::ENCRYPT provided to begin() must fail with
     * ErrorCode::KEY_EXPIRED.
     *
     * The value is a 64-bit integer representing milliseconds since January 1, 1970.
     *
     * Need not be hardware-enforced.
     */
    ORIGINATION_EXPIRE_DATETIME = 1610613137u /* TagType:DATE | 401 */,
    /**
     * Tag::USAGE_EXPIRE_DATETIME specifies the date and time at which the key expires for
     * verification and decryption purposes.  After this time, any attempt to use a key with
     * KeyPurpose::VERIFY or KeyPurpose::DECRYPT provided to begin() must fail with
     * ErrorCode::KEY_EXPIRED.
     *
     * The value is a 64-bit integer representing milliseconds since January 1, 1970.
     *
     * Need not be hardware-enforced.
     */
    USAGE_EXPIRE_DATETIME = 1610613138u /* TagType:DATE | 402 */,
    /**
     * Tag::MIN_SECONDS_BETWEEN_OPS specifies the minimum amount of time that elapses between
     * allowed operations using a key.  This can be used to rate-limit uses of keys in contexts
     * where unlimited use may enable brute force attacks.
     *
     * The value is a 32-bit integer representing seconds between allowed operations.
     *
     * When a key with this tag is used in an operation, the IKeymasterDevice must start a timer
     * during the finish() or abort() call.  Any call to begin() that is received before the timer
     * indicates that the interval specified by Tag::MIN_SECONDS_BETWEEN_OPS has elapsed must fail
     * with ErrorCode::KEY_RATE_LIMIT_EXCEEDED.  This implies that the IKeymasterDevice must keep a
     * table of use counters for keys with this tag.  Because memory is often limited, this table
     * may have a fixed maximum size and Keymaster may fail operations that attempt to use keys with
     * this tag when the table is full.  The table must acommodate at least 8 in-use keys and
     * aggressively reuse table slots when key minimum-usage intervals expire.  If an operation
     * fails because the table is full, Keymaster returns ErrorCode::TOO_MANY_OPERATIONS.
     *
     * Must be hardware-enforced.
     */
    MIN_SECONDS_BETWEEN_OPS = 805306771u /* TagType:UINT | 403 */,
    /**
     * Tag::MAX_USES_PER_BOOT specifies the maximum number of times that a key may be used between
     * system reboots.  This is another mechanism to rate-limit key use.
     *
     * The value is a 32-bit integer representing uses per boot.
     *
     * When a key with this tag is used in an operation, a key-associated counter must be
     * incremented during the begin() call.  After the key counter has exceeded this value, all
     * subsequent attempts to use the key must fail with ErrorCode::MAX_OPS_EXCEEDED, until the
     * device is restarted.  This implies that the IKeymasterDevice must keep a table of use
     * counters for keys with this tag.  Because Keymaster memory is often limited, this table can
     * have a fixed maximum size and Keymaster can fail operations that attempt to use keys with
     * this tag when the table is full.  The table needs to acommodate at least 8 keys.  If an
     * operation fails because the table is full, IKeymasterDevice must
     * ErrorCode::TOO_MANY_OPERATIONS.
     *
     * Must be hardware-enforced.
     */
    MAX_USES_PER_BOOT = 805306772u /* TagType:UINT | 404 */,
    /**
     * Tag::USER_ID specifies the ID of the Android user that is permitted to use the key.
     *
     * Must not be hardware-enforced.
     */
    USER_ID = 805306869u /* TagType:UINT | 501 */,
    /**
     * Tag::USER_SECURE_ID specifies that a key may only be used under a particular secure user
     * authentication state.  This tag is mutually exclusive with Tag::NO_AUTH_REQUIRED.
     *
     * The value is a 64-bit integer specifying the authentication policy state value which must be
     * present in the userId or authenticatorId field of a HardwareAuthToken provided to begin(),
     * update(), or finish().  If a key with Tag::USER_SECURE_ID is used without a HardwareAuthToken
     * with the matching userId or authenticatorId, the IKeymasterDevice must return
     * ErrorCode::KEY_USER_NOT_AUTHENTICATED.
     *
     * Tag::USER_SECURE_ID interacts with Tag::AUTH_TIMEOUT in a very important way.  If
     * Tag::AUTH_TIMEOUT is present in the key's characteristics then the key is a "timeout-based"
     * key, and may only be used if the difference between the current time when begin() is called
     * and the timestamp in the HardwareAuthToken is less than the value in Tag::AUTH_TIMEOUT * 1000
     * (the multiplier is because Tag::AUTH_TIMEOUT is in seconds, but the HardwareAuthToken
     * timestamp is in milliseconds).  Otherwise the IKeymasterDevice must returrn
     * ErrorCode::KEY_USER_NOT_AUTHENTICATED.
     *
     * If Tag::AUTH_TIMEOUT is not present, then the key is an "auth-per-operation" key.  In this
     * case, begin() must not require a HardwareAuthToken with appropriate contents.  Instead,
     * update() and finish() must receive a HardwareAuthToken with Tag::USER_SECURE_ID value in
     * userId or authenticatorId fields, and the current operation's operation handle in the
     * challenge field.  Otherwise the IKeymasterDevice must returrn
     * ErrorCode::KEY_USER_NOT_AUTHENTICATED.
     *
     * This tag is repeatable.  If repeated, and any one of the values matches the HardwareAuthToken
     * as described above, the key is authorized for use.  Otherwise the operation must fail with
     * ErrorCode::KEY_USER_NOT_AUTHENTICATED.
     *
     * Must be hardware-enforced.
     */
    USER_SECURE_ID = 2684355062u /* TagType:ULONG_REP | 502 */,
    /*
     * Secure ID of authorized user or authenticator(s).
     * Disallowed if NO_AUTH_REQUIRED is present.
     *
     *
     * Tag::NO_AUTH_REQUIRED specifies that no authentication is required to use this key.  This tag
     * is mutually exclusive with Tag::USER_SECURE_ID.
     *
     * Must be hardware-enforced.
     */
    NO_AUTH_REQUIRED = 1879048695u /* TagType:BOOL | 503 */,
    /*
     * If key is usable without authentication.
     *
     *
     * Tag::USER_AUTH_TYPE specifies the types of user authenticators that may be used to authorize
     * this key.
     *
     * The value is one or more values from HardwareAuthenticatorType, ORed together.
     *
     * When IKeymasterDevice is requested to perform an operation with a key with this tag, it must
     * receive a HardwareAuthToken and one or more bits must be set in both the HardwareAuthToken's
     * authenticatorType field and the Tag::USER_AUTH_TYPE value.  That is, it must be true that
     *
     *    (token.authenticatorType & tag_user_auth_type) != 0
     *
     * where token.authenticatorType is the authenticatorType field of the HardwareAuthToken and
     * tag_user_auth_type is the value of Tag:USER_AUTH_TYPE.
     *
     * Must be hardware-enforced.
     */
    USER_AUTH_TYPE = 268435960u /* TagType:ENUM | 504 */,
    /**
     * Tag::AUTH_TIMEOUT specifies the time in seconds for which the key is authorized for use,
     * after user authentication.  If
     * Tag::USER_SECURE_ID is present and this tag is not, then the key requies authentication for
     * every usage (see begin() for the details of the authentication-per-operation flow).
     *
     * The value is a 32-bit integer specifying the time in seconds after a successful
     * authentication of the user specified by Tag::USER_SECURE_ID with the authentication method
     * specified by Tag::USER_AUTH_TYPE that the key can be used.
     *
     * Must be hardware-enforced.
     */
    AUTH_TIMEOUT = 805306873u /* TagType:UINT | 505 */,
    /**
     * Tag::ALLOW_WHILE_ON_BODY specifies that the key may be used after authentication timeout if
     * device is still on-body (requires on-body sensor).
     *
     * Cannot be hardware-enforced.
     */
    ALLOW_WHILE_ON_BODY = 1879048698u /* TagType:BOOL | 506 */,
    /**
     * TRUSTED_USER_PRESENCE_REQUIRED is an optional feature that specifies that this key must be
     * unusable except when the user has provided proof of physical presence.  Proof of physical
     * presence must be a signal that cannot be triggered by an attacker who doesn't have one of:
     *
     *    a) Physical control of the device or
     *
     *    b) Control of the secure environment that holds the key.
     *
     * For instance, proof of user identity may be considered proof of presence if it meets the
     * requirements.  However, proof of identity established in one security domain (e.g. TEE) does
     * not constitute proof of presence in another security domain (e.g. StrongBox), and no
     * mechanism analogous to the authentication token is defined for communicating proof of
     * presence across security domains.
     *
     * Some examples:
     *
     *     A hardware button hardwired to a pin on a StrongBox device in such a way that nothing
     *     other than a button press can trigger the signal constitutes proof of physical presence
     *     for StrongBox keys.
     *
     *     Fingerprint authentication provides proof of presence (and identity) for TEE keys if the
     *     TEE has exclusive control of the fingerprint scanner and performs fingerprint matching.
     *
     *     Password authentication does not provide proof of presence to either TEE or StrongBox,
     *     even if TEE or StrongBox does the password matching, because password input is handled by
     *     the non-secure world, which means an attacker who has compromised Android can spoof
     *     password authentication.
     *
     * Note that no mechanism is defined for delivering proof of presence to an IKeymasterDevice,
     * except perhaps as implied by an auth token.  This means that Keymaster must be able to check
     * proof of presence some other way.  Further, the proof of presence must be performed between
     * begin() and the first call to update() or finish().  If the first update() or the finish()
     * call is made without proof of presence, the keymaster method must return
     * ErrorCode::PROOF_OF_PRESENCE_REQUIRED and abort the operation.  The caller must delay the
     * update() or finish() call until proof of presence has been provided, which means the caller
     * must also have some mechanism for verifying that the proof has been provided.
     *
     * Only one operation requiring TUP may be in flight at a time.  If begin() has already been
     * called on one key with TRUSTED_USER_PRESENCE_REQUIRED, and another begin() comes in for that
     * key or another with TRUSTED_USER_PRESENCE_REQUIRED, Keymaster must return
     * ErrorCode::CONCURRENT_PROOF_OF_PRESENCE_REQUESTED.
     *
     * Must be hardware-enforced.
     */
    TRUSTED_USER_PRESENCE_REQUIRED = 1879048699u /* TagType:BOOL | 507 */,
    /**
     * Tag::TRUSTED_CONFIRMATION_REQUIRED is only applicable to keys with KeyPurpose SIGN, and
     *  specifies that this key must not be usable unless the user provides confirmation of the data
     *  to be signed.  Confirmation is proven to keymaster via an approval token.  See
     *  CONFIRMATION_TOKEN, as well as the ConfirmatinUI HAL.
     *
     * If an attempt to use a key with this tag does not have a cryptographically valid
     * CONFIRMATION_TOKEN provided to finish() or if the data provided to update()/finish() does not
     * match the data described in the token, keymaster must return NO_USER_CONFIRMATION.
     *
     * Must be hardware-enforced.
     */
    TRUSTED_CONFIRMATION_REQUIRED = 1879048700u /* TagType:BOOL | 508 */,
    /**
     * Tag::UNLOCKED_DEVICE_REQUIRED specifies that the key may only be used when the device is
     * unlocked.
     *
     * Must be software-enforced.
     */
    UNLOCKED_DEVICE_REQUIRED = 1879048701u /* TagType:BOOL | 509 */,
    /**
     * Tag::APPLICATION_ID.  When provided to generateKey or importKey, this tag specifies data
     * that is necessary during all uses of the key.  In particular, calls to exportKey() and
     * getKeyCharacteristics() must provide the same value to the clientId parameter, and calls to
     * begin must provide this tag and the same associated data as part of the inParams set.  If
     * the correct data is not provided, the method must return ErrorCode::INVALID_KEY_BLOB.
     *
     * The content of this tag must be bound to the key cryptographically, meaning it must not be
     * possible for an adversary who has access to all of the secure world secrets but does not have
     * access to the tag content to decrypt the key without brute-forcing the tag content, which
     * applications can prevent by specifying sufficiently high-entropy content.
     *
     * Must never appear in KeyCharacteristics.
     */
    APPLICATION_ID = 2415919705u /* TagType:BYTES | 601 */,
    /*
     * Semantically unenforceable tags, either because they have no specific meaning or because
     * they're informational only.
     *
     *
     * Tag::APPLICATION_DATA.  When provided to generateKey or importKey, this tag specifies data
     * that is necessary during all uses of the key.  In particular, calls to exportKey() and
     * getKeyCharacteristics() must provide the same value to the appData parameter, and calls to
     * begin must provide this tag and the same associated data as part of the inParams set.  If
     * the correct data is not provided, the method must return ErrorCode::INVALID_KEY_BLOB.
     *
     * The content of this tag msut be bound to the key cryptographically, meaning it must not be
     * possible for an adversary who has access to all of the secure world secrets but does not have
     * access to the tag content to decrypt the key without brute-forcing the tag content, which
     * applications can prevent by specifying sufficiently high-entropy content.
     *
     * Must never appear in KeyCharacteristics.
     */
    APPLICATION_DATA = 2415919804u /* TagType:BYTES | 700 */,
    /**
     * Tag::CREATION_DATETIME specifies the date and time the key was created, in milliseconds since
     * January 1, 1970.  This tag is optional and informational only.
     *
     * Tag::CREATED is informational only, and not enforced by anything.  Must be in the
     * software-enforced list, if provided.
     */
    CREATION_DATETIME = 1610613437u /* TagType:DATE | 701 */,
    /**
     * Tag::ORIGIN specifies where the key was created, if known.  This tag must not be specified
     * during key generation or import, and must be added to the key characteristics by the
     * IKeymasterDevice.  The possible values are defined in the KeyOrigin enum.
     *
     * Must be hardware-enforced.
     */
    ORIGIN = 268436158u /* TagType:ENUM | 702 */,
    /**
     * Tag::ROOT_OF_TRUST specifies the root of trust, the key used by verified boot to validate the
     * operating system booted (if any).  This tag is never provided to or returned from Keymaster
     * in the key characteristics.  It exists only to define the tag for use in the attestation
     * record.
     *
     * Must never appear in KeyCharacteristics.
     */
    ROOT_OF_TRUST = 2415919808u /* TagType:BYTES | 704 */,
    /**
     * Tag::OS_VERSION specifies the system OS version with which the key may be used.  This tag is
     * never sent to the IKeymasterDevice, but is added to the hardware-enforced authorization list
     * by the TA.  Any attempt to use a key with a Tag::OS_VERSION value different from the
     * currently-running OS version must cause begin(), getKeyCharacteristics() or exportKey() to
     * return ErrorCode::KEY_REQUIRES_UPGRADE.  See upgradeKey() for details.
     *
     * The value of the tag is an integer of the form MMmmss, where MM is the major version number,
     * mm is the minor version number, and ss is the sub-minor version number.  For example, for a
     * key generated on Android version 4.0.3, the value would be 040003.
     *
     * The IKeymasterDevice HAL must read the current OS version from the system property
     * ro.build.version.release and deliver it to the secure environment when the HAL is first
     * loaded (mechanism is implementation-defined).  The secure environment must not accept another
     * version until after the next boot.  If the content of ro.build.version.release has additional
     * version information after the sub-minor version number, it must not be included in
     * Tag::OS_VERSION.  If the content is non-numeric, the secure environment must use 0 as the
     * system version.
     *
     * Must be hardware-enforced.
     */
    OS_VERSION = 805307073u /* TagType:UINT | 705 */,
    /**
     * Tag::OS_PATCHLEVEL specifies the system security patch level with which the key may be used.
     * This tag is never sent to the keymaster TA, but is added to the hardware-enforced
     * authorization list by the TA.  Any attempt to use a key with a Tag::OS_PATCHLEVEL value
     * different from the currently-running system patchlevel must cause begin(),
     * getKeyCharacteristics() or exportKey() to return ErrorCode::KEY_REQUIRES_UPGRADE.  See
     * upgradeKey() for details.
     *
     * The value of the tag is an integer of the form YYYYMM, where YYYY is the four-digit year of
     * the last update and MM is the two-digit month of the last update.  For example, for a key
     * generated on an Android device last updated in December 2015, the value would be 201512.
     *
     * The IKeymasterDevice HAL must read the current system patchlevel from the system property
     * ro.build.version.security_patch and deliver it to the secure environment when the HAL is
     * first loaded (mechanism is implementation-defined).  The secure environment must not accept
     * another patchlevel until after the next boot.
     *
     * Must be hardware-enforced.
     */
    OS_PATCHLEVEL = 805307074u /* TagType:UINT | 706 */,
    /**
     * Tag::UNIQUE_ID specifies a unique, time-based identifier.  This tag is never provided to or
     * returned from Keymaster in the key characteristics.  It exists only to define the tag for use
     * in the attestation record.
     *
     * When a key with Tag::INCLUDE_UNIQUE_ID is attested, the unique ID is added to the attestation
     * record.  The value is a 128-bit hash that is unique per device and per calling application,
     * and changes monthly and on most password resets.  It is computed with:
     *
     *    HMAC_SHA256(T || C || R, HBK)
     *
     * Where:
     *
     *    T is the "temporal counter value", computed by dividing the value of
     *      Tag::CREATION_DATETIME by 2592000000, dropping any remainder.  T changes every 30 days
     *      (2592000000 = 30 * 24 * 60 * 60 * 1000).
     *
     *    C is the value of Tag::ATTESTATION_APPLICATION_ID that is provided to attestKey().
     *
     *    R is 1 if Tag::RESET_SINCE_ID_ROTATION was provided to attestKey or 0 if the tag was not
     *      provided.
     *
     *    HBK is a unique hardware-bound secret known to the secure environment and never revealed
     *    by it.  The secret must contain at least 128 bits of entropy and be unique to the
     *    individual device (probabilistic uniqueness is acceptable).
     *
     *    HMAC_SHA256 is the HMAC function, with SHA-2-256 as the hash.
     *
     * The output of the HMAC function must be truncated to 128 bits.
     *
     * Must be hardware-enforced.
     */
    UNIQUE_ID = 2415919811u /* TagType:BYTES | 707 */,
    /**
     * Tag::ATTESTATION_CHALLENGE is used to deliver a "challenge" value to the attestKey() method,
     * which must place the value in the KeyDescription SEQUENCE of the attestation extension.  See
     * attestKey().
     *
     * Must never appear in KeyCharacteristics.
     */
    ATTESTATION_CHALLENGE = 2415919812u /* TagType:BYTES | 708 */,
    /*
     * Used to provide challenge in attestation
     *
     *
     * Tag::ATTESTATION_APPLICATION_ID identifies the set of applications which may use a key, used
     * only with attestKey().
     *
     * The content of Tag::ATTESTATION_APPLICATION_ID is a DER-encoded ASN.1 structure, with the
     * following schema:
     *
     * AttestationApplicationId ::= SEQUENCE {
     *     packageInfoRecords SET OF PackageInfoRecord,
     *     signatureDigests   SET OF OCTET_STRING,
     * }
     *
     * PackageInfoRecord ::= SEQUENCE {
     *     packageName        OCTET_STRING,
     *     version            INTEGER,
     * }
     *
     * See system/security/keystore/keystore_attestation_id.cpp for details of construction.
     * IKeymasterDevice implementers do not need to create or parse the ASN.1 structure, but only
     * copy the tag value into the attestation record.  The DER-encoded string must not exceed 1 KiB
     * in length.
     *
     * Cannot be hardware-enforced.
     */
    ATTESTATION_APPLICATION_ID = 2415919813u /* TagType:BYTES | 709 */,
    /**
     * Tag::ATTESTATION_ID_BRAND provides the device's brand name, as returned by Build.BRAND in
     * Android, to attestKey().  This field must be set only when requesting attestation of the
     * device's identifiers.
     *
     * If the device does not support ID attestation (or destroyAttestationIds() was previously
     * called and the device can no longer attest its IDs), any key attestation request that
     * includes this tag must fail with ErrorCode::CANNOT_ATTEST_IDS.
     *
     * Must never appear in KeyCharacteristics.
     */
    ATTESTATION_ID_BRAND = 2415919814u /* TagType:BYTES | 710 */,
    /**
     * Tag::ATTESTATION_ID_DEVICE provides the device's device name, as returned by Build.DEVICE in
     * Android, to attestKey().  This field must be set only when requesting attestation of the
     * device's identifiers.
     *
     * If the device does not support ID attestation (or destroyAttestationIds() was previously
     * called and the device can no longer attest its IDs), any key attestation request that
     * includes this tag must fail with ErrorCode::CANNOT_ATTEST_IDS.
     *
     * Must never appear in KeyCharacteristics.
     */
    ATTESTATION_ID_DEVICE = 2415919815u /* TagType:BYTES | 711 */,
    /**
     * Tag::ATTESTATION_ID_PRODUCT provides the device's product name, as returned by Build.PRODUCT
     * in Android, to attestKey().  This field must be set only when requesting attestation of the
     * device's identifiers.
     *
     * If the device does not support ID attestation (or destroyAttestationIds() was previously
     * called and the device can no longer attest its IDs), any key attestation request that
     * includes this tag must fail with ErrorCode::CANNOT_ATTEST_IDS.
     *
     * Must never appear in KeyCharacteristics.
     */
    ATTESTATION_ID_PRODUCT = 2415919816u /* TagType:BYTES | 712 */,
    /**
     * Tag::ATTESTATION_ID_SERIAL the device's serial number.  This field must be set only when
     * requesting attestation of the device's identifiers.
     *
     * If the device does not support ID attestation (or destroyAttestationIds() was previously
     * called and the device can no longer attest its IDs), any key attestation request that
     * includes this tag must fail with ErrorCode::CANNOT_ATTEST_IDS.
     *
     * Must never appear in KeyCharacteristics.
     */
    ATTESTATION_ID_SERIAL = 2415919817u /* TagType:BYTES | 713 */,
    /**
     * Tag::ATTESTATION_ID_IMEI provides the IMEIs for all radios on the device to attestKey().
     * This field must be set only when requesting attestation of the device's identifiers.
     *
     * If the device does not support ID attestation (or destroyAttestationIds() was previously
     * called and the device can no longer attest its IDs), any key attestation request that
     * includes this tag must fail with ErrorCode::CANNOT_ATTEST_IDS.
     *
     * Must never appear in KeyCharacteristics.
     */
    ATTESTATION_ID_IMEI = 2415919818u /* TagType:BYTES | 714 */,
    /*
     * Used to provide the device's IMEI to be included
     * in attestation
     *
     *
     * Tag::ATTESTATION_ID_MEID provides the MEIDs for all radios on the device to attestKey().
     * This field must be set only when requesting attestation of the device's identifiers.
     *
     * If the device does not support ID attestation (or destroyAttestationIds() was previously
     * called and the device can no longer attest its IDs), any key attestation request that
     * includes this tag must fail with ErrorCode::CANNOT_ATTEST_IDS.
     *
     * Must never appear in KeyCharacteristics.
     */
    ATTESTATION_ID_MEID = 2415919819u /* TagType:BYTES | 715 */,
    /*
     * Used to provide the device's MEID to be included
     * in attestation
     *
     *
     * Tag::ATTESTATION_ID_MANUFACTURER provides the device's manufacturer name, as returned by
     * Build.MANUFACTURER in Android, to attstKey().  This field must be set only when requesting
     * attestation of the device's identifiers.
     *
     * If the device does not support ID attestation (or destroyAttestationIds() was previously
     * called and the device can no longer attest its IDs), any key attestation request that
     * includes this tag must fail with ErrorCode::CANNOT_ATTEST_IDS.
     *
     * Must never appear in KeyCharacteristics.
     */
    ATTESTATION_ID_MANUFACTURER = 2415919820u /* TagType:BYTES | 716 */,
    /**
     * Tag::ATTESTATION_ID_MODEL provides the device's model name, as returned by Build.MODEL in
     * Android, to attestKey().  This field must be set only when requesting attestation of the
     * device's identifiers.
     *
     * If the device does not support ID attestation (or destroyAttestationIds() was previously
     * called and the device can no longer attest its IDs), any key attestation request that
     * includes this tag must fail with ErrorCode::CANNOT_ATTEST_IDS.
     *
     * Must never appear in KeyCharacteristics.
     */
    ATTESTATION_ID_MODEL = 2415919821u /* TagType:BYTES | 717 */,
    /**
     * Tag::VENDOR_PATCHLEVEL specifies the vendor image security patch level with which the key may
     * be used.  This tag is never sent to the keymaster TA, but is added to the hardware-enforced
     * authorization list by the TA.  Any attempt to use a key with a Tag::VENDOR_PATCHLEVEL value
     * different from the currently-running system patchlevel must cause begin(),
     * getKeyCharacteristics() or exportKey() to return ErrorCode::KEY_REQUIRES_UPGRADE.  See
     * upgradeKey() for details.
     *
     * The value of the tag is an integer of the form YYYYMMDD, where YYYY is the four-digit year of
     * the last update, MM is the two-digit month and DD is the two-digit day of the last
     * update.  For example, for a key generated on an Android device last updated on June 5, 2018,
     * the value would be 20180605.
     *
     * The IKeymasterDevice HAL must read the current vendor patchlevel from the system property
     * ro.vendor.build.security_patch and deliver it to the secure environment when the HAL is first
     * loaded (mechanism is implementation-defined).  The secure environment must not accept another
     * patchlevel until after the next boot.
     *
     * Must be hardware-enforced.
     */
    VENDOR_PATCHLEVEL = 805307086u /* TagType:UINT | 718 */,
    /**
     * Tag::BOOT_PATCHLEVEL specifies the boot image (kernel) security patch level with which the
     * key may be used.  This tag is never sent to the keymaster TA, but is added to the
     * hardware-enforced authorization list by the TA.  Any attempt to use a key with a
     * Tag::BOOT_PATCHLEVEL value different from the currently-running system patchlevel must
     * cause begin(), getKeyCharacteristics() or exportKey() to return
     * ErrorCode::KEY_REQUIRES_UPGRADE.  See upgradeKey() for details.
     *
     * The value of the tag is an integer of the form YYYYMMDD, where YYYY is the four-digit year of
     * the last update, MM is the two-digit month and DD is the two-digit day of the last
     * update.  For example, for a key generated on an Android device last updated on June 5, 2018,
     * the value would be 20180605.  If the day is not known, 00 may be substituted.
     *
     * During each boot, the bootloader must provide the patch level of the boot image to the secure
     * envirionment (mechanism is implementation-defined).
     *
     * Must be hardware-enforced.
     */
    BOOT_PATCHLEVEL = 805307087u /* TagType:UINT | 719 */,
    /**
     * Tag::ASSOCIATED_DATA Provides "associated data" for AES-GCM encryption or decryption.  This
     * tag is provided to update and specifies data that is not encrypted/decrypted, but is used in
     * computing the GCM tag.
     *
     * Must never appear KeyCharacteristics.
     */
    ASSOCIATED_DATA = 2415920104u /* TagType:BYTES | 1000 */,
    /**
     * Tag::NONCE is used to provide or return a nonce or Initialization Vector (IV) for AES-GCM,
     * AES-CBC, AES-CTR, or 3DES-CBC encryption or decryption.  This tag is provided to begin during
     * encryption and decryption operations.  It is only provided to begin if the key has
     * Tag::CALLER_NONCE.  If not provided, an appropriate nonce or IV must be randomly generated by
     * Keymaster and returned from begin.
     *
     * The value is a blob, an arbitrary-length array of bytes.  Allowed lengths depend on the mode:
     * GCM nonces are 12 bytes in length; AES-CBC and AES-CTR IVs are 16 bytes in length, 3DES-CBC
     * IVs are 8 bytes in length.
     *
     * Must never appear in KeyCharacteristics.
     */
    NONCE = 2415920105u /* TagType:BYTES | 1001 */,
    /**
     * Tag::MAC_LENGTH provides the requested length of a MAC or GCM authentication tag, in bits.
     *
     * The value is the MAC length in bits.  It must be a multiple of 8 and at least as large as the
     * value of Tag::MIN_MAC_LENGTH associated with the key.  Otherwise, begin() must return
     * ErrorCode::INVALID_MAC_LENGTH.
     *
     * Must never appear in KeyCharacteristics.
     */
    MAC_LENGTH = 805307371u /* TagType:UINT | 1003 */,
    /**
     * Tag::RESET_SINCE_ID_ROTATION specifies whether the device has been factory reset since the
     * last unique ID rotation.  Used for key attestation.
     *
     * Must never appear in KeyCharacteristics.
     */
    RESET_SINCE_ID_ROTATION = 1879049196u /* TagType:BOOL | 1004 */,
    /**
     * Tag::CONFIRMATION_TOKEN is used to deliver a cryptographic token proving that the user
     * confirmed a signing request.  The content is a full-length HMAC-SHA256 value.  See the
     * ConfirmationUI HAL for details of token computation.
     *
     * Must never appear in KeyCharacteristics.
     */
    CONFIRMATION_TOKEN = 2415920109u /* TagType:BYTES | 1005 */,
};

/**
 * Algorithms provided by IKeymasterDevice implementations.
 */
enum class Algorithm : uint32_t {
    /**
     * Asymmetric algorithms.
     */
    RSA = 1u,
    EC = 3u,
    /**
     * Block cipher algorithms
     */
    AES = 32u,
    TRIPLE_DES = 33u,
    /**
     * MAC algorithms
     */
    HMAC = 128u,
};

/**
 * Symmetric block cipher modes provided by keymaster implementations.
 */
enum class BlockMode : uint32_t {
    /*
     * Unauthenticated modes, usable only for encryption/decryption and not generally recommended
     * except for compatibility with existing other protocols.
     */
    ECB = 1u,
    CBC = 2u,
    CTR = 3u,
    /*
     * Authenticated modes, usable for encryption/decryption and signing/verification.  Recommended
     * over unauthenticated modes for all purposes.
     */
    GCM = 32u,
};

/**
 * Padding modes that may be applied to plaintext for encryption operations.  This list includes
 * padding modes for both symmetric and asymmetric algorithms.  Note that implementations should not
 * provide all possible combinations of algorithm and padding, only the
 * cryptographically-appropriate pairs.
 */
enum class PaddingMode : uint32_t {
    NONE = 1u,
    /*
     * deprecated
     */
    RSA_OAEP = 2u,
    RSA_PSS = 3u,
    RSA_PKCS1_1_5_ENCRYPT = 4u,
    RSA_PKCS1_1_5_SIGN = 5u,
    PKCS7 = 64u,
};

/**
 * Digests provided by keymaster implementations.
 */
enum class Digest : uint32_t {
    NONE = 0u,
    MD5 = 1u,
    SHA1 = 2u,
    SHA_2_224 = 3u,
    SHA_2_256 = 4u,
    SHA_2_384 = 5u,
    SHA_2_512 = 6u,
};

/**
 * Supported EC curves, used in ECDSA
 */
enum class EcCurve : uint32_t {
    P_224 = 0u,
    P_256 = 1u,
    P_384 = 2u,
    P_521 = 3u,
};

/**
 * The origin of a key (or pair), i.e. where it was generated.  Note that ORIGIN can be found in
 * either the hardware-enforced or software-enforced list for a key, indicating whether the key is
 * hardware or software-based.  Specifically, a key with GENERATED in the hardware-enforced list
 * must be guaranteed never to have existed outide the secure hardware.
 */
enum class KeyOrigin : uint32_t {
    /**
     * Generated in keymaster.  Should not exist outside the TEE.
     */
    GENERATED = 0u,
    /**
     * Derived inside keymaster.  Likely exists off-device.
     */
    DERIVED = 1u,
    /**
     * Imported into keymaster.  Existed as cleartext in Android.
     */
    IMPORTED = 2u,
    /**
     * Keymaster did not record origin.  This value can only be seen on keys in a keymaster0
     * implementation.  The keymaster0 adapter uses this value to document the fact that it is
     * unkown whether the key was generated inside or imported into keymaster.
     */
    UNKNOWN = 3u,
    /**
     * Securely imported into Keymaster.  Was created elsewhere, and passed securely through Android
     * to secure hardware.
     */
    SECURELY_IMPORTED = 4u,
};

/**
 * Usability requirements of key blobs.  This defines what system functionality must be available
 * for the key to function.  For example, key "blobs" which are actually handles referencing
 * encrypted key material stored in the file system cannot be used until the file system is
 * available, and should have BLOB_REQUIRES_FILE_SYSTEM.
 */
enum class KeyBlobUsageRequirements : uint32_t {
    STANDALONE = 0u,
    REQUIRES_FILE_SYSTEM = 1u,
};

/**
 * Possible purposes of a key (or pair).
 */
enum class KeyPurpose : uint32_t {
    ENCRYPT = 0u,
    /*
     * Usable with RSA, EC and AES keys.
     */
    DECRYPT = 1u,
    /*
     * Usable with RSA, EC and AES keys.
     */
    SIGN = 2u,
    /*
     * Usable with RSA, EC and HMAC keys.
     */
    VERIFY = 3u,
    /*
     * Usable with RSA, EC and HMAC keys.
     *
     *
     * 4 is reserved
     */
    WRAP_KEY = 5u,
};

/**
 * Keymaster error codes.
 */
enum class ErrorCode : int32_t {
    OK = 0,
    ROOT_OF_TRUST_ALREADY_SET = -1 /* -1 */,
    UNSUPPORTED_PURPOSE = -2 /* -2 */,
    INCOMPATIBLE_PURPOSE = -3 /* -3 */,
    UNSUPPORTED_ALGORITHM = -4 /* -4 */,
    INCOMPATIBLE_ALGORITHM = -5 /* -5 */,
    UNSUPPORTED_KEY_SIZE = -6 /* -6 */,
    UNSUPPORTED_BLOCK_MODE = -7 /* -7 */,
    INCOMPATIBLE_BLOCK_MODE = -8 /* -8 */,
    UNSUPPORTED_MAC_LENGTH = -9 /* -9 */,
    UNSUPPORTED_PADDING_MODE = -10 /* -10 */,
    INCOMPATIBLE_PADDING_MODE = -11 /* -11 */,
    UNSUPPORTED_DIGEST = -12 /* -12 */,
    INCOMPATIBLE_DIGEST = -13 /* -13 */,
    INVALID_EXPIRATION_TIME = -14 /* -14 */,
    INVALID_USER_ID = -15 /* -15 */,
    INVALID_AUTHORIZATION_TIMEOUT = -16 /* -16 */,
    UNSUPPORTED_KEY_FORMAT = -17 /* -17 */,
    INCOMPATIBLE_KEY_FORMAT = -18 /* -18 */,
    UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM = -19 /* -19 */,
    /**
     * For PKCS8 & PKCS12
     */
    UNSUPPORTED_KEY_VERIFICATION_ALGORITHM = -20 /* -20 */,
    /**
     * For PKCS8 & PKCS12
     */
    INVALID_INPUT_LENGTH = -21 /* -21 */,
    KEY_EXPORT_OPTIONS_INVALID = -22 /* -22 */,
    DELEGATION_NOT_ALLOWED = -23 /* -23 */,
    KEY_NOT_YET_VALID = -24 /* -24 */,
    KEY_EXPIRED = -25 /* -25 */,
    KEY_USER_NOT_AUTHENTICATED = -26 /* -26 */,
    OUTPUT_PARAMETER_NULL = -27 /* -27 */,
    INVALID_OPERATION_HANDLE = -28 /* -28 */,
    INSUFFICIENT_BUFFER_SPACE = -29 /* -29 */,
    VERIFICATION_FAILED = -30 /* -30 */,
    TOO_MANY_OPERATIONS = -31 /* -31 */,
    UNEXPECTED_NULL_POINTER = -32 /* -32 */,
    INVALID_KEY_BLOB = -33 /* -33 */,
    IMPORTED_KEY_NOT_ENCRYPTED = -34 /* -34 */,
    IMPORTED_KEY_DECRYPTION_FAILED = -35 /* -35 */,
    IMPORTED_KEY_NOT_SIGNED = -36 /* -36 */,
    IMPORTED_KEY_VERIFICATION_FAILED = -37 /* -37 */,
    INVALID_ARGUMENT = -38 /* -38 */,
    UNSUPPORTED_TAG = -39 /* -39 */,
    INVALID_TAG = -40 /* -40 */,
    MEMORY_ALLOCATION_FAILED = -41 /* -41 */,
    IMPORT_PARAMETER_MISMATCH = -44 /* -44 */,
    SECURE_HW_ACCESS_DENIED = -45 /* -45 */,
    OPERATION_CANCELLED = -46 /* -46 */,
    CONCURRENT_ACCESS_CONFLICT = -47 /* -47 */,
    SECURE_HW_BUSY = -48 /* -48 */,
    SECURE_HW_COMMUNICATION_FAILED = -49 /* -49 */,
    UNSUPPORTED_EC_FIELD = -50 /* -50 */,
    MISSING_NONCE = -51 /* -51 */,
    INVALID_NONCE = -52 /* -52 */,
    MISSING_MAC_LENGTH = -53 /* -53 */,
    KEY_RATE_LIMIT_EXCEEDED = -54 /* -54 */,
    CALLER_NONCE_PROHIBITED = -55 /* -55 */,
    KEY_MAX_OPS_EXCEEDED = -56 /* -56 */,
    INVALID_MAC_LENGTH = -57 /* -57 */,
    MISSING_MIN_MAC_LENGTH = -58 /* -58 */,
    UNSUPPORTED_MIN_MAC_LENGTH = -59 /* -59 */,
    UNSUPPORTED_KDF = -60 /* -60 */,
    UNSUPPORTED_EC_CURVE = -61 /* -61 */,
    KEY_REQUIRES_UPGRADE = -62 /* -62 */,
    ATTESTATION_CHALLENGE_MISSING = -63 /* -63 */,
    KEYMASTER_NOT_CONFIGURED = -64 /* -64 */,
    ATTESTATION_APPLICATION_ID_MISSING = -65 /* -65 */,
    CANNOT_ATTEST_IDS = -66 /* -66 */,
    ROLLBACK_RESISTANCE_UNAVAILABLE = -67 /* -67 */,
    HARDWARE_TYPE_UNAVAILABLE = -68 /* -68 */,
    PROOF_OF_PRESENCE_REQUIRED = -69 /* -69 */,
    CONCURRENT_PROOF_OF_PRESENCE_REQUESTED = -70 /* -70 */,
    NO_USER_CONFIRMATION = -71 /* -71 */,
    DEVICE_LOCKED = -72 /* -72 */,
    UNIMPLEMENTED = -100 /* -100 */,
    VERSION_MISMATCH = -101 /* -101 */,
    UNKNOWN_ERROR = -1000 /* -1000 */,
};

/**
 * Key derivation functions, mostly used in ECIES.
 */
enum class KeyDerivationFunction : uint32_t {
    /**
     * Do not apply a key derivation function; use the raw agreed key
     */
    NONE = 0u,
    /**
     * HKDF defined in RFC 5869 with SHA256
     */
    RFC5869_SHA256 = 1u,
    /**
     * KDF1 defined in ISO 18033-2 with SHA1
     */
    ISO18033_2_KDF1_SHA1 = 2u,
    /**
     * KDF1 defined in ISO 18033-2 with SHA256
     */
    ISO18033_2_KDF1_SHA256 = 3u,
    /**
     * KDF2 defined in ISO 18033-2 with SHA1
     */
    ISO18033_2_KDF2_SHA1 = 4u,
    /**
     * KDF2 defined in ISO 18033-2 with SHA256
     */
    ISO18033_2_KDF2_SHA256 = 5u,
};

/**
 * Hardware authentication type, used by HardwareAuthTokens to specify the mechanism used to
 * authentiate the user, and in KeyCharacteristics to specify the allowable mechanisms for
 * authenticating to activate a key.
 */
enum class HardwareAuthenticatorType : uint32_t {
    NONE = 0u,
    PASSWORD = 1u /* 1 << 0 */,
    FINGERPRINT = 2u /* 1 << 1 */,
    ANY = 4294967295u /* 0xFFFFFFFF */,
};

/**
 * Device security levels.
 */
enum class SecurityLevel : uint32_t {
    SOFTWARE = 0u,
    TRUSTED_ENVIRONMENT = 1u,
    /**
     * STRONGBOX specifies that the secure hardware satisfies the requirements specified in CDD
     * 9.11.2.
     */
    STRONGBOX = 2u,
};

/**
 * Formats for key import and export.
 */
enum class KeyFormat : uint32_t {
    /**
     * X.509 certificate format, for public key export.
     */
    X509 = 0u,
    /**
     * PCKS#8 format, asymmetric key pair import.
     */
    PKCS8 = 1u,
    /**
     * Raw bytes, for symmetric key import.
     */
    RAW = 3u,
};

struct KeyParameter final {
    // Forward declaration for forward reference support:
    union IntegerParams;

    union IntegerParams final {
        /*
         * Enum types
         */
        ::android::hardware::keymaster::V4_0::Algorithm algorithm __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V4_0::BlockMode blockMode __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V4_0::PaddingMode paddingMode __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V4_0::Digest digest __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V4_0::EcCurve ecCurve __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V4_0::KeyOrigin origin __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements keyBlobUsageRequirements __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V4_0::KeyPurpose purpose __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V4_0::KeyDerivationFunction keyDerivationFunction __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType hardwareAuthenticatorType __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V4_0::SecurityLevel hardwareType __attribute__ ((aligned(4)));
        /*
         * Other types
         */
        bool boolValue __attribute__ ((aligned(1)));
        uint32_t integer __attribute__ ((aligned(4)));
        uint64_t longInteger __attribute__ ((aligned(8)));
        uint64_t dateTime __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, algorithm) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, blockMode) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, paddingMode) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, digest) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, ecCurve) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, origin) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, keyBlobUsageRequirements) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, purpose) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, keyDerivationFunction) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, hardwareAuthenticatorType) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, hardwareType) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, boolValue) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, integer) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, longInteger) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams, dateTime) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams) == 8, "wrong alignment");

    /**
     * Discriminates the union/blob field used.  The blob cannot be placed in the union, but only
     * one of "f" and "blob" may ever be used at a time.
     */
    ::android::hardware::keymaster::V4_0::Tag tag __attribute__ ((aligned(4)));
    ::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams f __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<uint8_t> blob __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter, tag) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter, f) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyParameter, blob) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::keymaster::V4_0::KeyParameter) == 32, "wrong size");
static_assert(__alignof(::android::hardware::keymaster::V4_0::KeyParameter) == 8, "wrong alignment");

/**
 * KeyCharacteristics defines the attributes of a key, including cryptographic parameters, and usage
 * restrictions.  It consits of two vectors of KeyParameters, one for "softwareEnforced" attributes
 * and one for "hardwareEnforced" attributes.
 *
 * KeyCharacteristics objects are returned by generateKey, importKey, importWrappedKey and
 * getKeyCharacteristics.  The IKeymasterDevice secure environment is responsible for allocating the
 * parameters, all of which are Tags with associated values, to the correct vector.  The
 * hardwareEnforced vector must contain only those attributes which are enforced by secure hardware.
 * All others should be in the softwareEnforced vector.  See the definitions of individual Tag enums
 * for specification of which must be hardware-enforced, which may be software-enforced and which
 * must never appear in KeyCharacteristics.
 */
struct KeyCharacteristics final {
    ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter> softwareEnforced __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter> hardwareEnforced __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyCharacteristics, softwareEnforced) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::KeyCharacteristics, hardwareEnforced) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::keymaster::V4_0::KeyCharacteristics) == 32, "wrong size");
static_assert(__alignof(::android::hardware::keymaster::V4_0::KeyCharacteristics) == 8, "wrong alignment");

/**
 * HardwareAuthToken is used to prove successful user authentication, to unlock the use of a key.
 *
 * HardwareAuthTokens are produced by other secure environment applications, notably GateKeeper and
 * Fingerprint, in response to successful user authentication events.  These tokens are passed to
 * begin(), update(), and finish() to prove that authentication occurred.  See those methods for
 * more details.  It is up to the caller to determine which of the generated auth tokens is
 * appropriate for a given key operation.
 */
struct HardwareAuthToken final {
    /**
     * challenge is a value that's used to enable authentication tokens to authorize specific
     * events.  The primary use case for challenge is to authorize an IKeymasterDevice cryptographic
     * operation, for keys that require authentication per operation. See begin() for details.
     */
    uint64_t challenge __attribute__ ((aligned(8)));
    /**
     *  userId is the a "secure" user ID.  It is not related to any Android user ID or UID, but is
     *  created in the Gatekeeper application in the secure environment.
     */
    uint64_t userId __attribute__ ((aligned(8)));
    /**
     *  authenticatorId is the a "secure" user ID.  It is not related to any Android user ID or UID,
     *  but is created in an authentication application in the secure environment, such as the
     *  Fingerprint application.
     */
    uint64_t authenticatorId __attribute__ ((aligned(8)));
    /**
     * authenticatorType describes the type of authentication that took place, e.g. password or
     * fingerprint.
     */
    ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType authenticatorType __attribute__ ((aligned(4)));
    /**
     * timestamp indicates when the user authentication took place, in milliseconds since some
     * starting point (generally the most recent device boot) which all of the applications within
     * one secure environment must agree upon.  This timestamp is used to determine whether or not
     * the authentication occurred recently enough to unlock a key (see Tag::AUTH_TIMEOUT).
     */
    uint64_t timestamp __attribute__ ((aligned(8)));
    /**
     * MACs are computed with a backward-compatible method, used by Keymaster 3.0, Gatekeeper 1.0
     * and Fingerprint 1.0, as well as pre-treble HALs.
     *
     * The MAC is Constants::AUTH_TOKEN_MAC_LENGTH bytes in length and is computed as follows:
     *
     *     HMAC_SHA256(
     *         H, 0 || challenge || user_id || authenticator_id || authenticator_type || timestamp)
     *
     * where ``||'' represents concatenation, the leading zero is a single byte, and all integers
     * are represented as unsigned values, the full width of the type.  The challenge, userId and
     * authenticatorId values are in machine order, but authenticatorType and timestamp are in
     * network order (big-endian).  This odd construction is compatible with the hw_auth_token_t
     * structure,
     *
     * Note that mac is a vec rather than an array, not because it's actually variable-length but
     * because it could be empty.  As documented in the IKeymasterDevice::begin,
     * IKeymasterDevice::update and IKeymasterDevice::finish doc comments, an empty mac indicates
     * that this auth token is empty.
     */
    ::android::hardware::hidl_vec<uint8_t> mac __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::keymaster::V4_0::HardwareAuthToken, challenge) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::HardwareAuthToken, userId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::HardwareAuthToken, authenticatorId) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::HardwareAuthToken, authenticatorType) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::HardwareAuthToken, timestamp) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::HardwareAuthToken, mac) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::keymaster::V4_0::HardwareAuthToken) == 56, "wrong size");
static_assert(__alignof(::android::hardware::keymaster::V4_0::HardwareAuthToken) == 8, "wrong alignment");

typedef uint64_t OperationHandle;

/**
 * HmacSharingParameters holds the data used in the process of establishing a shared HMAC key
 * between multiple Keymaster instances.  Sharing parameters are returned in this struct by
 * getHmacSharingParameters() and send to computeSharedHmac().  See the named methods in IKeymaster
 * for details of usage.
 */
struct HmacSharingParameters final {
    /**
     * Either empty or contains a persistent value that is associated with the pre-shared HMAC
     * agreement key (see documentation of computeSharedHmac in @4.0::IKeymaster).  It is either
     * empty or 32 bytes in length.
     */
    ::android::hardware::hidl_vec<uint8_t> seed __attribute__ ((aligned(8)));
    /**
     * A 32-byte value which is guaranteed to be different each time
     * getHmacSharingParameters() is called.  Probabilistic uniqueness (i.e. random) is acceptable,
     * though a stronger uniqueness guarantee (e.g. counter) is recommended where possible.
     */
    ::android::hardware::hidl_array<uint8_t, 32> nonce __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::keymaster::V4_0::HmacSharingParameters, seed) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::HmacSharingParameters, nonce) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::keymaster::V4_0::HmacSharingParameters) == 48, "wrong size");
static_assert(__alignof(::android::hardware::keymaster::V4_0::HmacSharingParameters) == 8, "wrong alignment");

/**
 * VerificationToken enables one Keymaster instance to validate authorizations for another.  See
 * verifyAuthorizations() in IKeymaster for details.
 */
struct VerificationToken final {
    /**
     * The operation handle, used to ensure freshness.
     */
    uint64_t challenge __attribute__ ((aligned(8)));
    /**
     * The current time of the secure environment that generates the VerificationToken.  This can be
     * checked against auth tokens generated by the same secure environment, which avoids needing to
     * synchronize clocks.
     */
    uint64_t timestamp __attribute__ ((aligned(8)));
    /**
     * A list of the parameters verified.  Empty if the only parameters verified are time-related.
     * In that case the timestamp is the payload.
     */
    ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter> parametersVerified __attribute__ ((aligned(8)));
    /**
     * SecurityLevel of the secure environment that generated the token.
     */
    ::android::hardware::keymaster::V4_0::SecurityLevel securityLevel __attribute__ ((aligned(4)));
    /**
     * 32-byte HMAC-SHA256 of the above values, computed as:
     *
     *    HMAC(H,
     *         "Auth Verification" || challenge || timestamp || securityLevel || parametersVerified)
     *
     * where:
     *
     *   ``HMAC'' is the shared HMAC key (see computeSharedHmac() in IKeymaster).
     *
     *   ``||'' represents concatenation
     *
     * The representation of challenge and timestamp is as 64-bit unsigned integers in big-endian
     * order.  securityLevel is represented as a 32-bit unsigned integer in big-endian order.
     *
     * If parametersVerified is non-empty, the representation of parametersVerified is an ASN.1 DER
     * encoded representation of the values.  The ASN.1 schema used is the AuthorizationList schema
     * from the Keystore attestation documentation.  If parametersVerified is empty, it is simply
     * omitted from the HMAC computation.
     */
    ::android::hardware::hidl_vec<uint8_t> mac __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::keymaster::V4_0::VerificationToken, challenge) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::VerificationToken, timestamp) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::VerificationToken, parametersVerified) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::VerificationToken, securityLevel) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V4_0::VerificationToken, mac) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::keymaster::V4_0::VerificationToken) == 56, "wrong size");
static_assert(__alignof(::android::hardware::keymaster::V4_0::VerificationToken) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::Constants o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::Constants o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::Constants lhs, const ::android::hardware::keymaster::V4_0::Constants rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::Constants rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::Constants lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::Constants lhs, const ::android::hardware::keymaster::V4_0::Constants rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::Constants rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::Constants lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::Constants e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::Constants e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::TagType o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::TagType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::TagType lhs, const ::android::hardware::keymaster::V4_0::TagType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::TagType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::TagType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::TagType lhs, const ::android::hardware::keymaster::V4_0::TagType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::TagType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::TagType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::TagType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::TagType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::Tag o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::Tag o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::Tag lhs, const ::android::hardware::keymaster::V4_0::Tag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::Tag rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::Tag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::Tag lhs, const ::android::hardware::keymaster::V4_0::Tag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::Tag rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::Tag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::Tag e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::Tag e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::Algorithm o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::Algorithm o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::Algorithm lhs, const ::android::hardware::keymaster::V4_0::Algorithm rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::Algorithm rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::Algorithm lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::Algorithm lhs, const ::android::hardware::keymaster::V4_0::Algorithm rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::Algorithm rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::Algorithm lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::Algorithm e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::Algorithm e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::BlockMode o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::BlockMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::BlockMode lhs, const ::android::hardware::keymaster::V4_0::BlockMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::BlockMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::BlockMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::BlockMode lhs, const ::android::hardware::keymaster::V4_0::BlockMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::BlockMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::BlockMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::BlockMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::BlockMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::PaddingMode o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::PaddingMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::PaddingMode lhs, const ::android::hardware::keymaster::V4_0::PaddingMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::PaddingMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::PaddingMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::PaddingMode lhs, const ::android::hardware::keymaster::V4_0::PaddingMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::PaddingMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::PaddingMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::PaddingMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::PaddingMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::Digest o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::Digest o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::Digest lhs, const ::android::hardware::keymaster::V4_0::Digest rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::Digest rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::Digest lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::Digest lhs, const ::android::hardware::keymaster::V4_0::Digest rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::Digest rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::Digest lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::Digest e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::Digest e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::EcCurve o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::EcCurve o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::EcCurve lhs, const ::android::hardware::keymaster::V4_0::EcCurve rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::EcCurve rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::EcCurve lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::EcCurve lhs, const ::android::hardware::keymaster::V4_0::EcCurve rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::EcCurve rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::EcCurve lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::EcCurve e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::EcCurve e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::KeyOrigin o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::KeyOrigin o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::KeyOrigin lhs, const ::android::hardware::keymaster::V4_0::KeyOrigin rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::KeyOrigin rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::KeyOrigin lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::KeyOrigin lhs, const ::android::hardware::keymaster::V4_0::KeyOrigin rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::KeyOrigin rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::KeyOrigin lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::KeyOrigin e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::KeyOrigin e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements lhs, const ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements lhs, const ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::KeyPurpose o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::KeyPurpose o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::KeyPurpose lhs, const ::android::hardware::keymaster::V4_0::KeyPurpose rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::KeyPurpose rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::KeyPurpose lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::KeyPurpose lhs, const ::android::hardware::keymaster::V4_0::KeyPurpose rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::KeyPurpose rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::KeyPurpose lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::KeyPurpose e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::KeyPurpose e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::ErrorCode o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::ErrorCode o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::keymaster::V4_0::ErrorCode lhs, const ::android::hardware::keymaster::V4_0::ErrorCode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::keymaster::V4_0::ErrorCode rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::keymaster::V4_0::ErrorCode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::keymaster::V4_0::ErrorCode lhs, const ::android::hardware::keymaster::V4_0::ErrorCode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::keymaster::V4_0::ErrorCode rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::keymaster::V4_0::ErrorCode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::keymaster::V4_0::ErrorCode e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::keymaster::V4_0::ErrorCode e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::KeyDerivationFunction o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::KeyDerivationFunction o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::KeyDerivationFunction lhs, const ::android::hardware::keymaster::V4_0::KeyDerivationFunction rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::KeyDerivationFunction rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::KeyDerivationFunction lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::KeyDerivationFunction lhs, const ::android::hardware::keymaster::V4_0::KeyDerivationFunction rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::KeyDerivationFunction rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::KeyDerivationFunction lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::KeyDerivationFunction e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::KeyDerivationFunction e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::HardwareAuthenticatorType o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::HardwareAuthenticatorType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType lhs, const ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType lhs, const ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::SecurityLevel o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::SecurityLevel o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::SecurityLevel lhs, const ::android::hardware::keymaster::V4_0::SecurityLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::SecurityLevel rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::SecurityLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::SecurityLevel lhs, const ::android::hardware::keymaster::V4_0::SecurityLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::SecurityLevel rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::SecurityLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::SecurityLevel e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::SecurityLevel e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V4_0::KeyFormat o);
static inline void PrintTo(::android::hardware::keymaster::V4_0::KeyFormat o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::KeyFormat lhs, const ::android::hardware::keymaster::V4_0::KeyFormat rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::KeyFormat rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V4_0::KeyFormat lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::KeyFormat lhs, const ::android::hardware::keymaster::V4_0::KeyFormat rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V4_0::KeyFormat rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V4_0::KeyFormat lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V4_0::KeyFormat e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V4_0::KeyFormat e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams& o);
static inline void PrintTo(const ::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams& o, ::std::ostream*);
// operator== and operator!= are not generated for IntegerParams

static inline std::string toString(const ::android::hardware::keymaster::V4_0::KeyParameter& o);
static inline void PrintTo(const ::android::hardware::keymaster::V4_0::KeyParameter& o, ::std::ostream*);
// operator== and operator!= are not generated for KeyParameter

static inline std::string toString(const ::android::hardware::keymaster::V4_0::KeyCharacteristics& o);
static inline void PrintTo(const ::android::hardware::keymaster::V4_0::KeyCharacteristics& o, ::std::ostream*);
// operator== and operator!= are not generated for KeyCharacteristics

static inline std::string toString(const ::android::hardware::keymaster::V4_0::HardwareAuthToken& o);
static inline void PrintTo(const ::android::hardware::keymaster::V4_0::HardwareAuthToken& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::keymaster::V4_0::HardwareAuthToken& lhs, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& rhs);
static inline bool operator!=(const ::android::hardware::keymaster::V4_0::HardwareAuthToken& lhs, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& rhs);

static inline std::string toString(const ::android::hardware::keymaster::V4_0::HmacSharingParameters& o);
static inline void PrintTo(const ::android::hardware::keymaster::V4_0::HmacSharingParameters& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::keymaster::V4_0::HmacSharingParameters& lhs, const ::android::hardware::keymaster::V4_0::HmacSharingParameters& rhs);
static inline bool operator!=(const ::android::hardware::keymaster::V4_0::HmacSharingParameters& lhs, const ::android::hardware::keymaster::V4_0::HmacSharingParameters& rhs);

static inline std::string toString(const ::android::hardware::keymaster::V4_0::VerificationToken& o);
static inline void PrintTo(const ::android::hardware::keymaster::V4_0::VerificationToken& o, ::std::ostream*);
// operator== and operator!= are not generated for VerificationToken

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::Constants>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::Constants> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::Constants::AUTH_TOKEN_MAC_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Constants::AUTH_TOKEN_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "AUTH_TOKEN_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Constants::AUTH_TOKEN_MAC_LENGTH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::Constants o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::Constants::AUTH_TOKEN_MAC_LENGTH) {
        return "AUTH_TOKEN_MAC_LENGTH";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::Constants o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::TagType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::TagType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::TagType::INVALID) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::INVALID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::TagType::ENUM) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::ENUM)) {
        os += (first ? "" : " | ");
        os += "ENUM";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::ENUM;
    }
    if ((o & ::android::hardware::keymaster::V4_0::TagType::ENUM_REP) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::ENUM_REP)) {
        os += (first ? "" : " | ");
        os += "ENUM_REP";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::ENUM_REP;
    }
    if ((o & ::android::hardware::keymaster::V4_0::TagType::UINT) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::UINT)) {
        os += (first ? "" : " | ");
        os += "UINT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::UINT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::TagType::UINT_REP) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::UINT_REP)) {
        os += (first ? "" : " | ");
        os += "UINT_REP";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::UINT_REP;
    }
    if ((o & ::android::hardware::keymaster::V4_0::TagType::ULONG) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::ULONG)) {
        os += (first ? "" : " | ");
        os += "ULONG";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::ULONG;
    }
    if ((o & ::android::hardware::keymaster::V4_0::TagType::DATE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::DATE)) {
        os += (first ? "" : " | ");
        os += "DATE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::DATE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::TagType::BOOL) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::BOOL)) {
        os += (first ? "" : " | ");
        os += "BOOL";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::BOOL;
    }
    if ((o & ::android::hardware::keymaster::V4_0::TagType::BIGNUM) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::BIGNUM)) {
        os += (first ? "" : " | ");
        os += "BIGNUM";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::BIGNUM;
    }
    if ((o & ::android::hardware::keymaster::V4_0::TagType::BYTES) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::BYTES)) {
        os += (first ? "" : " | ");
        os += "BYTES";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::BYTES;
    }
    if ((o & ::android::hardware::keymaster::V4_0::TagType::ULONG_REP) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::TagType::ULONG_REP)) {
        os += (first ? "" : " | ");
        os += "ULONG_REP";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::TagType::ULONG_REP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::TagType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::TagType::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::keymaster::V4_0::TagType::ENUM) {
        return "ENUM";
    }
    if (o == ::android::hardware::keymaster::V4_0::TagType::ENUM_REP) {
        return "ENUM_REP";
    }
    if (o == ::android::hardware::keymaster::V4_0::TagType::UINT) {
        return "UINT";
    }
    if (o == ::android::hardware::keymaster::V4_0::TagType::UINT_REP) {
        return "UINT_REP";
    }
    if (o == ::android::hardware::keymaster::V4_0::TagType::ULONG) {
        return "ULONG";
    }
    if (o == ::android::hardware::keymaster::V4_0::TagType::DATE) {
        return "DATE";
    }
    if (o == ::android::hardware::keymaster::V4_0::TagType::BOOL) {
        return "BOOL";
    }
    if (o == ::android::hardware::keymaster::V4_0::TagType::BIGNUM) {
        return "BIGNUM";
    }
    if (o == ::android::hardware::keymaster::V4_0::TagType::BYTES) {
        return "BYTES";
    }
    if (o == ::android::hardware::keymaster::V4_0::TagType::ULONG_REP) {
        return "ULONG_REP";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::TagType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::Tag>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::Tag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::Tag::INVALID) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::INVALID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::PURPOSE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::PURPOSE)) {
        os += (first ? "" : " | ");
        os += "PURPOSE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::PURPOSE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ALGORITHM) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ALGORITHM)) {
        os += (first ? "" : " | ");
        os += "ALGORITHM";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ALGORITHM;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::KEY_SIZE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::KEY_SIZE)) {
        os += (first ? "" : " | ");
        os += "KEY_SIZE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::KEY_SIZE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::BLOCK_MODE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::BLOCK_MODE)) {
        os += (first ? "" : " | ");
        os += "BLOCK_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::BLOCK_MODE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::DIGEST) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::DIGEST)) {
        os += (first ? "" : " | ");
        os += "DIGEST";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::DIGEST;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::PADDING) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::PADDING)) {
        os += (first ? "" : " | ");
        os += "PADDING";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::PADDING;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::CALLER_NONCE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::CALLER_NONCE)) {
        os += (first ? "" : " | ");
        os += "CALLER_NONCE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::CALLER_NONCE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::MIN_MAC_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::MIN_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "MIN_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::MIN_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::EC_CURVE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::EC_CURVE)) {
        os += (first ? "" : " | ");
        os += "EC_CURVE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::EC_CURVE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::RSA_PUBLIC_EXPONENT) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::RSA_PUBLIC_EXPONENT)) {
        os += (first ? "" : " | ");
        os += "RSA_PUBLIC_EXPONENT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::RSA_PUBLIC_EXPONENT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::INCLUDE_UNIQUE_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::INCLUDE_UNIQUE_ID)) {
        os += (first ? "" : " | ");
        os += "INCLUDE_UNIQUE_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::INCLUDE_UNIQUE_ID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::BLOB_USAGE_REQUIREMENTS) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::BLOB_USAGE_REQUIREMENTS)) {
        os += (first ? "" : " | ");
        os += "BLOB_USAGE_REQUIREMENTS";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::BLOB_USAGE_REQUIREMENTS;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::BOOTLOADER_ONLY) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::BOOTLOADER_ONLY)) {
        os += (first ? "" : " | ");
        os += "BOOTLOADER_ONLY";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::BOOTLOADER_ONLY;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ROLLBACK_RESISTANCE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ROLLBACK_RESISTANCE)) {
        os += (first ? "" : " | ");
        os += "ROLLBACK_RESISTANCE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ROLLBACK_RESISTANCE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::HARDWARE_TYPE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::HARDWARE_TYPE)) {
        os += (first ? "" : " | ");
        os += "HARDWARE_TYPE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::HARDWARE_TYPE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ACTIVE_DATETIME) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ACTIVE_DATETIME)) {
        os += (first ? "" : " | ");
        os += "ACTIVE_DATETIME";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ACTIVE_DATETIME;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ORIGINATION_EXPIRE_DATETIME) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ORIGINATION_EXPIRE_DATETIME)) {
        os += (first ? "" : " | ");
        os += "ORIGINATION_EXPIRE_DATETIME";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ORIGINATION_EXPIRE_DATETIME;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::USAGE_EXPIRE_DATETIME) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::USAGE_EXPIRE_DATETIME)) {
        os += (first ? "" : " | ");
        os += "USAGE_EXPIRE_DATETIME";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::USAGE_EXPIRE_DATETIME;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::MIN_SECONDS_BETWEEN_OPS) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::MIN_SECONDS_BETWEEN_OPS)) {
        os += (first ? "" : " | ");
        os += "MIN_SECONDS_BETWEEN_OPS";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::MIN_SECONDS_BETWEEN_OPS;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::MAX_USES_PER_BOOT) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::MAX_USES_PER_BOOT)) {
        os += (first ? "" : " | ");
        os += "MAX_USES_PER_BOOT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::MAX_USES_PER_BOOT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::USER_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::USER_ID)) {
        os += (first ? "" : " | ");
        os += "USER_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::USER_ID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::USER_SECURE_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::USER_SECURE_ID)) {
        os += (first ? "" : " | ");
        os += "USER_SECURE_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::USER_SECURE_ID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::NO_AUTH_REQUIRED) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::NO_AUTH_REQUIRED)) {
        os += (first ? "" : " | ");
        os += "NO_AUTH_REQUIRED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::NO_AUTH_REQUIRED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::USER_AUTH_TYPE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::USER_AUTH_TYPE)) {
        os += (first ? "" : " | ");
        os += "USER_AUTH_TYPE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::USER_AUTH_TYPE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::AUTH_TIMEOUT) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::AUTH_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "AUTH_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::AUTH_TIMEOUT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ALLOW_WHILE_ON_BODY) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ALLOW_WHILE_ON_BODY)) {
        os += (first ? "" : " | ");
        os += "ALLOW_WHILE_ON_BODY";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ALLOW_WHILE_ON_BODY;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::TRUSTED_USER_PRESENCE_REQUIRED) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::TRUSTED_USER_PRESENCE_REQUIRED)) {
        os += (first ? "" : " | ");
        os += "TRUSTED_USER_PRESENCE_REQUIRED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::TRUSTED_USER_PRESENCE_REQUIRED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::TRUSTED_CONFIRMATION_REQUIRED) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::TRUSTED_CONFIRMATION_REQUIRED)) {
        os += (first ? "" : " | ");
        os += "TRUSTED_CONFIRMATION_REQUIRED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::TRUSTED_CONFIRMATION_REQUIRED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::UNLOCKED_DEVICE_REQUIRED) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::UNLOCKED_DEVICE_REQUIRED)) {
        os += (first ? "" : " | ");
        os += "UNLOCKED_DEVICE_REQUIRED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::UNLOCKED_DEVICE_REQUIRED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::APPLICATION_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::APPLICATION_ID)) {
        os += (first ? "" : " | ");
        os += "APPLICATION_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::APPLICATION_ID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::APPLICATION_DATA) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::APPLICATION_DATA)) {
        os += (first ? "" : " | ");
        os += "APPLICATION_DATA";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::APPLICATION_DATA;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::CREATION_DATETIME) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::CREATION_DATETIME)) {
        os += (first ? "" : " | ");
        os += "CREATION_DATETIME";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::CREATION_DATETIME;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ORIGIN) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ORIGIN)) {
        os += (first ? "" : " | ");
        os += "ORIGIN";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ORIGIN;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ROOT_OF_TRUST) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ROOT_OF_TRUST)) {
        os += (first ? "" : " | ");
        os += "ROOT_OF_TRUST";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ROOT_OF_TRUST;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::OS_VERSION) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::OS_VERSION)) {
        os += (first ? "" : " | ");
        os += "OS_VERSION";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::OS_VERSION;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::OS_PATCHLEVEL) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::OS_PATCHLEVEL)) {
        os += (first ? "" : " | ");
        os += "OS_PATCHLEVEL";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::OS_PATCHLEVEL;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::UNIQUE_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::UNIQUE_ID)) {
        os += (first ? "" : " | ");
        os += "UNIQUE_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::UNIQUE_ID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_CHALLENGE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ATTESTATION_CHALLENGE)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_CHALLENGE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_CHALLENGE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_APPLICATION_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ATTESTATION_APPLICATION_ID)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_APPLICATION_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_APPLICATION_ID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_BRAND) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_BRAND)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_BRAND";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_BRAND;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_DEVICE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_DEVICE)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_DEVICE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_DEVICE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_PRODUCT) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_PRODUCT)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_PRODUCT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_PRODUCT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_SERIAL) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_SERIAL)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_SERIAL";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_SERIAL;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_IMEI) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_IMEI)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_IMEI";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_IMEI;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MEID) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MEID)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_MEID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MEID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MANUFACTURER) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MANUFACTURER)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_MANUFACTURER";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MANUFACTURER;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MODEL) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MODEL)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_MODEL";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MODEL;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::VENDOR_PATCHLEVEL) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::VENDOR_PATCHLEVEL)) {
        os += (first ? "" : " | ");
        os += "VENDOR_PATCHLEVEL";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::VENDOR_PATCHLEVEL;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::BOOT_PATCHLEVEL) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::BOOT_PATCHLEVEL)) {
        os += (first ? "" : " | ");
        os += "BOOT_PATCHLEVEL";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::BOOT_PATCHLEVEL;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::ASSOCIATED_DATA) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::ASSOCIATED_DATA)) {
        os += (first ? "" : " | ");
        os += "ASSOCIATED_DATA";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::ASSOCIATED_DATA;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::NONCE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::NONCE)) {
        os += (first ? "" : " | ");
        os += "NONCE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::NONCE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::MAC_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::RESET_SINCE_ID_ROTATION) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::RESET_SINCE_ID_ROTATION)) {
        os += (first ? "" : " | ");
        os += "RESET_SINCE_ID_ROTATION";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::RESET_SINCE_ID_ROTATION;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Tag::CONFIRMATION_TOKEN) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Tag::CONFIRMATION_TOKEN)) {
        os += (first ? "" : " | ");
        os += "CONFIRMATION_TOKEN";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Tag::CONFIRMATION_TOKEN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::Tag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::Tag::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::PURPOSE) {
        return "PURPOSE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ALGORITHM) {
        return "ALGORITHM";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::KEY_SIZE) {
        return "KEY_SIZE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::BLOCK_MODE) {
        return "BLOCK_MODE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::DIGEST) {
        return "DIGEST";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::PADDING) {
        return "PADDING";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::CALLER_NONCE) {
        return "CALLER_NONCE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::MIN_MAC_LENGTH) {
        return "MIN_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::EC_CURVE) {
        return "EC_CURVE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::RSA_PUBLIC_EXPONENT) {
        return "RSA_PUBLIC_EXPONENT";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::INCLUDE_UNIQUE_ID) {
        return "INCLUDE_UNIQUE_ID";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::BLOB_USAGE_REQUIREMENTS) {
        return "BLOB_USAGE_REQUIREMENTS";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::BOOTLOADER_ONLY) {
        return "BOOTLOADER_ONLY";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ROLLBACK_RESISTANCE) {
        return "ROLLBACK_RESISTANCE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::HARDWARE_TYPE) {
        return "HARDWARE_TYPE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ACTIVE_DATETIME) {
        return "ACTIVE_DATETIME";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ORIGINATION_EXPIRE_DATETIME) {
        return "ORIGINATION_EXPIRE_DATETIME";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::USAGE_EXPIRE_DATETIME) {
        return "USAGE_EXPIRE_DATETIME";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::MIN_SECONDS_BETWEEN_OPS) {
        return "MIN_SECONDS_BETWEEN_OPS";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::MAX_USES_PER_BOOT) {
        return "MAX_USES_PER_BOOT";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::USER_ID) {
        return "USER_ID";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::USER_SECURE_ID) {
        return "USER_SECURE_ID";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::NO_AUTH_REQUIRED) {
        return "NO_AUTH_REQUIRED";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::USER_AUTH_TYPE) {
        return "USER_AUTH_TYPE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::AUTH_TIMEOUT) {
        return "AUTH_TIMEOUT";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ALLOW_WHILE_ON_BODY) {
        return "ALLOW_WHILE_ON_BODY";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::TRUSTED_USER_PRESENCE_REQUIRED) {
        return "TRUSTED_USER_PRESENCE_REQUIRED";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::TRUSTED_CONFIRMATION_REQUIRED) {
        return "TRUSTED_CONFIRMATION_REQUIRED";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::UNLOCKED_DEVICE_REQUIRED) {
        return "UNLOCKED_DEVICE_REQUIRED";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::APPLICATION_ID) {
        return "APPLICATION_ID";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::APPLICATION_DATA) {
        return "APPLICATION_DATA";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::CREATION_DATETIME) {
        return "CREATION_DATETIME";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ORIGIN) {
        return "ORIGIN";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ROOT_OF_TRUST) {
        return "ROOT_OF_TRUST";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::OS_VERSION) {
        return "OS_VERSION";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::OS_PATCHLEVEL) {
        return "OS_PATCHLEVEL";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::UNIQUE_ID) {
        return "UNIQUE_ID";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_CHALLENGE) {
        return "ATTESTATION_CHALLENGE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_APPLICATION_ID) {
        return "ATTESTATION_APPLICATION_ID";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_BRAND) {
        return "ATTESTATION_ID_BRAND";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_DEVICE) {
        return "ATTESTATION_ID_DEVICE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_PRODUCT) {
        return "ATTESTATION_ID_PRODUCT";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_SERIAL) {
        return "ATTESTATION_ID_SERIAL";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_IMEI) {
        return "ATTESTATION_ID_IMEI";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MEID) {
        return "ATTESTATION_ID_MEID";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MANUFACTURER) {
        return "ATTESTATION_ID_MANUFACTURER";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MODEL) {
        return "ATTESTATION_ID_MODEL";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::VENDOR_PATCHLEVEL) {
        return "VENDOR_PATCHLEVEL";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::BOOT_PATCHLEVEL) {
        return "BOOT_PATCHLEVEL";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::ASSOCIATED_DATA) {
        return "ASSOCIATED_DATA";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::NONCE) {
        return "NONCE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::MAC_LENGTH) {
        return "MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::RESET_SINCE_ID_ROTATION) {
        return "RESET_SINCE_ID_ROTATION";
    }
    if (o == ::android::hardware::keymaster::V4_0::Tag::CONFIRMATION_TOKEN) {
        return "CONFIRMATION_TOKEN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::Tag o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::Algorithm>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::Algorithm> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::Algorithm::RSA) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Algorithm::RSA)) {
        os += (first ? "" : " | ");
        os += "RSA";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Algorithm::RSA;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Algorithm::EC) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Algorithm::EC)) {
        os += (first ? "" : " | ");
        os += "EC";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Algorithm::EC;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Algorithm::AES) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Algorithm::AES)) {
        os += (first ? "" : " | ");
        os += "AES";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Algorithm::AES;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Algorithm::TRIPLE_DES) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Algorithm::TRIPLE_DES)) {
        os += (first ? "" : " | ");
        os += "TRIPLE_DES";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Algorithm::TRIPLE_DES;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Algorithm::HMAC) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Algorithm::HMAC)) {
        os += (first ? "" : " | ");
        os += "HMAC";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Algorithm::HMAC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::Algorithm o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::Algorithm::RSA) {
        return "RSA";
    }
    if (o == ::android::hardware::keymaster::V4_0::Algorithm::EC) {
        return "EC";
    }
    if (o == ::android::hardware::keymaster::V4_0::Algorithm::AES) {
        return "AES";
    }
    if (o == ::android::hardware::keymaster::V4_0::Algorithm::TRIPLE_DES) {
        return "TRIPLE_DES";
    }
    if (o == ::android::hardware::keymaster::V4_0::Algorithm::HMAC) {
        return "HMAC";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::Algorithm o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::BlockMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::BlockMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::BlockMode::ECB) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::BlockMode::ECB)) {
        os += (first ? "" : " | ");
        os += "ECB";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::BlockMode::ECB;
    }
    if ((o & ::android::hardware::keymaster::V4_0::BlockMode::CBC) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::BlockMode::CBC)) {
        os += (first ? "" : " | ");
        os += "CBC";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::BlockMode::CBC;
    }
    if ((o & ::android::hardware::keymaster::V4_0::BlockMode::CTR) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::BlockMode::CTR)) {
        os += (first ? "" : " | ");
        os += "CTR";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::BlockMode::CTR;
    }
    if ((o & ::android::hardware::keymaster::V4_0::BlockMode::GCM) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::BlockMode::GCM)) {
        os += (first ? "" : " | ");
        os += "GCM";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::BlockMode::GCM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::BlockMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::BlockMode::ECB) {
        return "ECB";
    }
    if (o == ::android::hardware::keymaster::V4_0::BlockMode::CBC) {
        return "CBC";
    }
    if (o == ::android::hardware::keymaster::V4_0::BlockMode::CTR) {
        return "CTR";
    }
    if (o == ::android::hardware::keymaster::V4_0::BlockMode::GCM) {
        return "GCM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::BlockMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::PaddingMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::PaddingMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::PaddingMode::NONE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::PaddingMode::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::PaddingMode::NONE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::PaddingMode::RSA_OAEP) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::PaddingMode::RSA_OAEP)) {
        os += (first ? "" : " | ");
        os += "RSA_OAEP";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::PaddingMode::RSA_OAEP;
    }
    if ((o & ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PSS) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::PaddingMode::RSA_PSS)) {
        os += (first ? "" : " | ");
        os += "RSA_PSS";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PSS;
    }
    if ((o & ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PKCS1_1_5_ENCRYPT) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::PaddingMode::RSA_PKCS1_1_5_ENCRYPT)) {
        os += (first ? "" : " | ");
        os += "RSA_PKCS1_1_5_ENCRYPT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PKCS1_1_5_ENCRYPT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PKCS1_1_5_SIGN) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::PaddingMode::RSA_PKCS1_1_5_SIGN)) {
        os += (first ? "" : " | ");
        os += "RSA_PKCS1_1_5_SIGN";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PKCS1_1_5_SIGN;
    }
    if ((o & ::android::hardware::keymaster::V4_0::PaddingMode::PKCS7) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::PaddingMode::PKCS7)) {
        os += (first ? "" : " | ");
        os += "PKCS7";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::PaddingMode::PKCS7;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::PaddingMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::PaddingMode::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::keymaster::V4_0::PaddingMode::RSA_OAEP) {
        return "RSA_OAEP";
    }
    if (o == ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PSS) {
        return "RSA_PSS";
    }
    if (o == ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PKCS1_1_5_ENCRYPT) {
        return "RSA_PKCS1_1_5_ENCRYPT";
    }
    if (o == ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PKCS1_1_5_SIGN) {
        return "RSA_PKCS1_1_5_SIGN";
    }
    if (o == ::android::hardware::keymaster::V4_0::PaddingMode::PKCS7) {
        return "PKCS7";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::PaddingMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::Digest>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::Digest> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::Digest::NONE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Digest::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Digest::NONE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Digest::MD5) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Digest::MD5)) {
        os += (first ? "" : " | ");
        os += "MD5";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Digest::MD5;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Digest::SHA1) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Digest::SHA1)) {
        os += (first ? "" : " | ");
        os += "SHA1";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Digest::SHA1;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Digest::SHA_2_224) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Digest::SHA_2_224)) {
        os += (first ? "" : " | ");
        os += "SHA_2_224";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Digest::SHA_2_224;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Digest::SHA_2_256) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Digest::SHA_2_256)) {
        os += (first ? "" : " | ");
        os += "SHA_2_256";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Digest::SHA_2_256;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Digest::SHA_2_384) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Digest::SHA_2_384)) {
        os += (first ? "" : " | ");
        os += "SHA_2_384";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Digest::SHA_2_384;
    }
    if ((o & ::android::hardware::keymaster::V4_0::Digest::SHA_2_512) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::Digest::SHA_2_512)) {
        os += (first ? "" : " | ");
        os += "SHA_2_512";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::Digest::SHA_2_512;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::Digest o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::Digest::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::keymaster::V4_0::Digest::MD5) {
        return "MD5";
    }
    if (o == ::android::hardware::keymaster::V4_0::Digest::SHA1) {
        return "SHA1";
    }
    if (o == ::android::hardware::keymaster::V4_0::Digest::SHA_2_224) {
        return "SHA_2_224";
    }
    if (o == ::android::hardware::keymaster::V4_0::Digest::SHA_2_256) {
        return "SHA_2_256";
    }
    if (o == ::android::hardware::keymaster::V4_0::Digest::SHA_2_384) {
        return "SHA_2_384";
    }
    if (o == ::android::hardware::keymaster::V4_0::Digest::SHA_2_512) {
        return "SHA_2_512";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::Digest o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::EcCurve>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::EcCurve> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::EcCurve::P_224) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::EcCurve::P_224)) {
        os += (first ? "" : " | ");
        os += "P_224";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::EcCurve::P_224;
    }
    if ((o & ::android::hardware::keymaster::V4_0::EcCurve::P_256) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::EcCurve::P_256)) {
        os += (first ? "" : " | ");
        os += "P_256";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::EcCurve::P_256;
    }
    if ((o & ::android::hardware::keymaster::V4_0::EcCurve::P_384) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::EcCurve::P_384)) {
        os += (first ? "" : " | ");
        os += "P_384";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::EcCurve::P_384;
    }
    if ((o & ::android::hardware::keymaster::V4_0::EcCurve::P_521) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::EcCurve::P_521)) {
        os += (first ? "" : " | ");
        os += "P_521";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::EcCurve::P_521;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::EcCurve o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::EcCurve::P_224) {
        return "P_224";
    }
    if (o == ::android::hardware::keymaster::V4_0::EcCurve::P_256) {
        return "P_256";
    }
    if (o == ::android::hardware::keymaster::V4_0::EcCurve::P_384) {
        return "P_384";
    }
    if (o == ::android::hardware::keymaster::V4_0::EcCurve::P_521) {
        return "P_521";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::EcCurve o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::KeyOrigin>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::KeyOrigin> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::KeyOrigin::GENERATED) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyOrigin::GENERATED)) {
        os += (first ? "" : " | ");
        os += "GENERATED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyOrigin::GENERATED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyOrigin::DERIVED) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyOrigin::DERIVED)) {
        os += (first ? "" : " | ");
        os += "DERIVED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyOrigin::DERIVED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyOrigin::IMPORTED) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyOrigin::IMPORTED)) {
        os += (first ? "" : " | ");
        os += "IMPORTED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyOrigin::IMPORTED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyOrigin::UNKNOWN) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyOrigin::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyOrigin::UNKNOWN;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyOrigin::SECURELY_IMPORTED) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyOrigin::SECURELY_IMPORTED)) {
        os += (first ? "" : " | ");
        os += "SECURELY_IMPORTED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyOrigin::SECURELY_IMPORTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::KeyOrigin o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::KeyOrigin::GENERATED) {
        return "GENERATED";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyOrigin::DERIVED) {
        return "DERIVED";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyOrigin::IMPORTED) {
        return "IMPORTED";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyOrigin::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyOrigin::SECURELY_IMPORTED) {
        return "SECURELY_IMPORTED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::KeyOrigin o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements::STANDALONE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements::STANDALONE)) {
        os += (first ? "" : " | ");
        os += "STANDALONE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements::STANDALONE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements::REQUIRES_FILE_SYSTEM) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements::REQUIRES_FILE_SYSTEM)) {
        os += (first ? "" : " | ");
        os += "REQUIRES_FILE_SYSTEM";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements::REQUIRES_FILE_SYSTEM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements::STANDALONE) {
        return "STANDALONE";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements::REQUIRES_FILE_SYSTEM) {
        return "REQUIRES_FILE_SYSTEM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::KeyPurpose>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::KeyPurpose> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::KeyPurpose::ENCRYPT) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyPurpose::ENCRYPT)) {
        os += (first ? "" : " | ");
        os += "ENCRYPT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyPurpose::ENCRYPT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyPurpose::DECRYPT) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyPurpose::DECRYPT)) {
        os += (first ? "" : " | ");
        os += "DECRYPT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyPurpose::DECRYPT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyPurpose::SIGN) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyPurpose::SIGN)) {
        os += (first ? "" : " | ");
        os += "SIGN";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyPurpose::SIGN;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyPurpose::VERIFY) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyPurpose::VERIFY)) {
        os += (first ? "" : " | ");
        os += "VERIFY";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyPurpose::VERIFY;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyPurpose::WRAP_KEY) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyPurpose::WRAP_KEY)) {
        os += (first ? "" : " | ");
        os += "WRAP_KEY";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyPurpose::WRAP_KEY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::KeyPurpose o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::KeyPurpose::ENCRYPT) {
        return "ENCRYPT";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyPurpose::DECRYPT) {
        return "DECRYPT";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyPurpose::SIGN) {
        return "SIGN";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyPurpose::VERIFY) {
        return "VERIFY";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyPurpose::WRAP_KEY) {
        return "WRAP_KEY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::KeyPurpose o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::ErrorCode>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::ErrorCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::OK) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::OK;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::ROOT_OF_TRUST_ALREADY_SET) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::ROOT_OF_TRUST_ALREADY_SET)) {
        os += (first ? "" : " | ");
        os += "ROOT_OF_TRUST_ALREADY_SET";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::ROOT_OF_TRUST_ALREADY_SET;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_PURPOSE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_PURPOSE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_PURPOSE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_PURPOSE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_PURPOSE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_PURPOSE)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_PURPOSE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_PURPOSE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_ALGORITHM) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_ALGORITHM)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_ALGORITHM";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_ALGORITHM;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_ALGORITHM) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_ALGORITHM)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_ALGORITHM";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_ALGORITHM;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_SIZE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_SIZE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_KEY_SIZE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_SIZE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_BLOCK_MODE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_BLOCK_MODE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_BLOCK_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_BLOCK_MODE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_BLOCK_MODE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_BLOCK_MODE)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_BLOCK_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_BLOCK_MODE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_MAC_LENGTH) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_PADDING_MODE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_PADDING_MODE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_PADDING_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_PADDING_MODE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_PADDING_MODE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_PADDING_MODE)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_PADDING_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_PADDING_MODE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_DIGEST) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_DIGEST)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_DIGEST";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_DIGEST;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_DIGEST) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_DIGEST)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_DIGEST";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_DIGEST;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_EXPIRATION_TIME) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INVALID_EXPIRATION_TIME)) {
        os += (first ? "" : " | ");
        os += "INVALID_EXPIRATION_TIME";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_EXPIRATION_TIME;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_USER_ID) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INVALID_USER_ID)) {
        os += (first ? "" : " | ");
        os += "INVALID_USER_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_USER_ID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_AUTHORIZATION_TIMEOUT) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INVALID_AUTHORIZATION_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "INVALID_AUTHORIZATION_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_AUTHORIZATION_TIMEOUT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_FORMAT) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_FORMAT)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_KEY_FORMAT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_FORMAT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_KEY_FORMAT) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_KEY_FORMAT)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_KEY_FORMAT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_KEY_FORMAT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_VERIFICATION_ALGORITHM) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_VERIFICATION_ALGORITHM)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_KEY_VERIFICATION_ALGORITHM";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_VERIFICATION_ALGORITHM;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_INPUT_LENGTH) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INVALID_INPUT_LENGTH)) {
        os += (first ? "" : " | ");
        os += "INVALID_INPUT_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_INPUT_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::KEY_EXPORT_OPTIONS_INVALID) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::KEY_EXPORT_OPTIONS_INVALID)) {
        os += (first ? "" : " | ");
        os += "KEY_EXPORT_OPTIONS_INVALID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::KEY_EXPORT_OPTIONS_INVALID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::DELEGATION_NOT_ALLOWED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::DELEGATION_NOT_ALLOWED)) {
        os += (first ? "" : " | ");
        os += "DELEGATION_NOT_ALLOWED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::DELEGATION_NOT_ALLOWED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::KEY_NOT_YET_VALID) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::KEY_NOT_YET_VALID)) {
        os += (first ? "" : " | ");
        os += "KEY_NOT_YET_VALID";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::KEY_NOT_YET_VALID;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::KEY_EXPIRED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::KEY_EXPIRED)) {
        os += (first ? "" : " | ");
        os += "KEY_EXPIRED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::KEY_EXPIRED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::KEY_USER_NOT_AUTHENTICATED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::KEY_USER_NOT_AUTHENTICATED)) {
        os += (first ? "" : " | ");
        os += "KEY_USER_NOT_AUTHENTICATED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::KEY_USER_NOT_AUTHENTICATED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::OUTPUT_PARAMETER_NULL) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::OUTPUT_PARAMETER_NULL)) {
        os += (first ? "" : " | ");
        os += "OUTPUT_PARAMETER_NULL";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::OUTPUT_PARAMETER_NULL;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_OPERATION_HANDLE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INVALID_OPERATION_HANDLE)) {
        os += (first ? "" : " | ");
        os += "INVALID_OPERATION_HANDLE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_OPERATION_HANDLE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INSUFFICIENT_BUFFER_SPACE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INSUFFICIENT_BUFFER_SPACE)) {
        os += (first ? "" : " | ");
        os += "INSUFFICIENT_BUFFER_SPACE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INSUFFICIENT_BUFFER_SPACE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::VERIFICATION_FAILED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::VERIFICATION_FAILED)) {
        os += (first ? "" : " | ");
        os += "VERIFICATION_FAILED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::VERIFICATION_FAILED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::TOO_MANY_OPERATIONS) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::TOO_MANY_OPERATIONS)) {
        os += (first ? "" : " | ");
        os += "TOO_MANY_OPERATIONS";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::TOO_MANY_OPERATIONS;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNEXPECTED_NULL_POINTER) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNEXPECTED_NULL_POINTER)) {
        os += (first ? "" : " | ");
        os += "UNEXPECTED_NULL_POINTER";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNEXPECTED_NULL_POINTER;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_KEY_BLOB) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INVALID_KEY_BLOB)) {
        os += (first ? "" : " | ");
        os += "INVALID_KEY_BLOB";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_KEY_BLOB;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_NOT_ENCRYPTED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_NOT_ENCRYPTED)) {
        os += (first ? "" : " | ");
        os += "IMPORTED_KEY_NOT_ENCRYPTED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_NOT_ENCRYPTED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_DECRYPTION_FAILED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_DECRYPTION_FAILED)) {
        os += (first ? "" : " | ");
        os += "IMPORTED_KEY_DECRYPTION_FAILED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_DECRYPTION_FAILED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_NOT_SIGNED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_NOT_SIGNED)) {
        os += (first ? "" : " | ");
        os += "IMPORTED_KEY_NOT_SIGNED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_NOT_SIGNED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_VERIFICATION_FAILED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_VERIFICATION_FAILED)) {
        os += (first ? "" : " | ");
        os += "IMPORTED_KEY_VERIFICATION_FAILED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_VERIFICATION_FAILED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_ARGUMENT) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INVALID_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_ARGUMENT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_TAG) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_TAG)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_TAG";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_TAG;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_TAG) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INVALID_TAG)) {
        os += (first ? "" : " | ");
        os += "INVALID_TAG";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_TAG;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::MEMORY_ALLOCATION_FAILED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::MEMORY_ALLOCATION_FAILED)) {
        os += (first ? "" : " | ");
        os += "MEMORY_ALLOCATION_FAILED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::MEMORY_ALLOCATION_FAILED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::IMPORT_PARAMETER_MISMATCH) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::IMPORT_PARAMETER_MISMATCH)) {
        os += (first ? "" : " | ");
        os += "IMPORT_PARAMETER_MISMATCH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::IMPORT_PARAMETER_MISMATCH;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_ACCESS_DENIED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_ACCESS_DENIED)) {
        os += (first ? "" : " | ");
        os += "SECURE_HW_ACCESS_DENIED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_ACCESS_DENIED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::OPERATION_CANCELLED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::OPERATION_CANCELLED)) {
        os += (first ? "" : " | ");
        os += "OPERATION_CANCELLED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::OPERATION_CANCELLED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::CONCURRENT_ACCESS_CONFLICT) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::CONCURRENT_ACCESS_CONFLICT)) {
        os += (first ? "" : " | ");
        os += "CONCURRENT_ACCESS_CONFLICT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::CONCURRENT_ACCESS_CONFLICT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_BUSY) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_BUSY)) {
        os += (first ? "" : " | ");
        os += "SECURE_HW_BUSY";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_BUSY;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_COMMUNICATION_FAILED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_COMMUNICATION_FAILED)) {
        os += (first ? "" : " | ");
        os += "SECURE_HW_COMMUNICATION_FAILED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_COMMUNICATION_FAILED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_EC_FIELD) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_EC_FIELD)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_EC_FIELD";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_EC_FIELD;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_NONCE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::MISSING_NONCE)) {
        os += (first ? "" : " | ");
        os += "MISSING_NONCE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_NONCE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_NONCE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INVALID_NONCE)) {
        os += (first ? "" : " | ");
        os += "INVALID_NONCE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_NONCE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_MAC_LENGTH) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::MISSING_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "MISSING_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::KEY_RATE_LIMIT_EXCEEDED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::KEY_RATE_LIMIT_EXCEEDED)) {
        os += (first ? "" : " | ");
        os += "KEY_RATE_LIMIT_EXCEEDED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::KEY_RATE_LIMIT_EXCEEDED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::CALLER_NONCE_PROHIBITED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::CALLER_NONCE_PROHIBITED)) {
        os += (first ? "" : " | ");
        os += "CALLER_NONCE_PROHIBITED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::CALLER_NONCE_PROHIBITED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::KEY_MAX_OPS_EXCEEDED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::KEY_MAX_OPS_EXCEEDED)) {
        os += (first ? "" : " | ");
        os += "KEY_MAX_OPS_EXCEEDED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::KEY_MAX_OPS_EXCEEDED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_MAC_LENGTH) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::INVALID_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "INVALID_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_MIN_MAC_LENGTH) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::MISSING_MIN_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "MISSING_MIN_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_MIN_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_MIN_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KDF) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KDF)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_KDF";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KDF;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_EC_CURVE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_EC_CURVE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_EC_CURVE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_EC_CURVE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::KEY_REQUIRES_UPGRADE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::KEY_REQUIRES_UPGRADE)) {
        os += (first ? "" : " | ");
        os += "KEY_REQUIRES_UPGRADE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::KEY_REQUIRES_UPGRADE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::ATTESTATION_CHALLENGE_MISSING) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::ATTESTATION_CHALLENGE_MISSING)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_CHALLENGE_MISSING";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::ATTESTATION_CHALLENGE_MISSING;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::KEYMASTER_NOT_CONFIGURED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::KEYMASTER_NOT_CONFIGURED)) {
        os += (first ? "" : " | ");
        os += "KEYMASTER_NOT_CONFIGURED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::KEYMASTER_NOT_CONFIGURED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::ATTESTATION_APPLICATION_ID_MISSING) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::ATTESTATION_APPLICATION_ID_MISSING)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_APPLICATION_ID_MISSING";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::ATTESTATION_APPLICATION_ID_MISSING;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::CANNOT_ATTEST_IDS) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::CANNOT_ATTEST_IDS)) {
        os += (first ? "" : " | ");
        os += "CANNOT_ATTEST_IDS";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::CANNOT_ATTEST_IDS;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::ROLLBACK_RESISTANCE_UNAVAILABLE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::ROLLBACK_RESISTANCE_UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "ROLLBACK_RESISTANCE_UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::ROLLBACK_RESISTANCE_UNAVAILABLE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::HARDWARE_TYPE_UNAVAILABLE) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::HARDWARE_TYPE_UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "HARDWARE_TYPE_UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::HARDWARE_TYPE_UNAVAILABLE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::PROOF_OF_PRESENCE_REQUIRED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::PROOF_OF_PRESENCE_REQUIRED)) {
        os += (first ? "" : " | ");
        os += "PROOF_OF_PRESENCE_REQUIRED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::PROOF_OF_PRESENCE_REQUIRED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::CONCURRENT_PROOF_OF_PRESENCE_REQUESTED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::CONCURRENT_PROOF_OF_PRESENCE_REQUESTED)) {
        os += (first ? "" : " | ");
        os += "CONCURRENT_PROOF_OF_PRESENCE_REQUESTED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::CONCURRENT_PROOF_OF_PRESENCE_REQUESTED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::NO_USER_CONFIRMATION) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::NO_USER_CONFIRMATION)) {
        os += (first ? "" : " | ");
        os += "NO_USER_CONFIRMATION";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::NO_USER_CONFIRMATION;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::DEVICE_LOCKED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::DEVICE_LOCKED)) {
        os += (first ? "" : " | ");
        os += "DEVICE_LOCKED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::DEVICE_LOCKED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNIMPLEMENTED) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNIMPLEMENTED)) {
        os += (first ? "" : " | ");
        os += "UNIMPLEMENTED";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNIMPLEMENTED;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::VERSION_MISMATCH) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::VERSION_MISMATCH)) {
        os += (first ? "" : " | ");
        os += "VERSION_MISMATCH";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::VERSION_MISMATCH;
    }
    if ((o & ::android::hardware::keymaster::V4_0::ErrorCode::UNKNOWN_ERROR) == static_cast<int32_t>(::android::hardware::keymaster::V4_0::ErrorCode::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::ErrorCode::UNKNOWN_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::ErrorCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::OK) {
        return "OK";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::ROOT_OF_TRUST_ALREADY_SET) {
        return "ROOT_OF_TRUST_ALREADY_SET";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_PURPOSE) {
        return "UNSUPPORTED_PURPOSE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_PURPOSE) {
        return "INCOMPATIBLE_PURPOSE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_ALGORITHM) {
        return "UNSUPPORTED_ALGORITHM";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_ALGORITHM) {
        return "INCOMPATIBLE_ALGORITHM";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_SIZE) {
        return "UNSUPPORTED_KEY_SIZE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_BLOCK_MODE) {
        return "UNSUPPORTED_BLOCK_MODE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_BLOCK_MODE) {
        return "INCOMPATIBLE_BLOCK_MODE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_MAC_LENGTH) {
        return "UNSUPPORTED_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_PADDING_MODE) {
        return "UNSUPPORTED_PADDING_MODE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_PADDING_MODE) {
        return "INCOMPATIBLE_PADDING_MODE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_DIGEST) {
        return "UNSUPPORTED_DIGEST";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_DIGEST) {
        return "INCOMPATIBLE_DIGEST";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_EXPIRATION_TIME) {
        return "INVALID_EXPIRATION_TIME";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_USER_ID) {
        return "INVALID_USER_ID";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_AUTHORIZATION_TIMEOUT) {
        return "INVALID_AUTHORIZATION_TIMEOUT";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_FORMAT) {
        return "UNSUPPORTED_KEY_FORMAT";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_KEY_FORMAT) {
        return "INCOMPATIBLE_KEY_FORMAT";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM) {
        return "UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_VERIFICATION_ALGORITHM) {
        return "UNSUPPORTED_KEY_VERIFICATION_ALGORITHM";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_INPUT_LENGTH) {
        return "INVALID_INPUT_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::KEY_EXPORT_OPTIONS_INVALID) {
        return "KEY_EXPORT_OPTIONS_INVALID";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::DELEGATION_NOT_ALLOWED) {
        return "DELEGATION_NOT_ALLOWED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::KEY_NOT_YET_VALID) {
        return "KEY_NOT_YET_VALID";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::KEY_EXPIRED) {
        return "KEY_EXPIRED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::KEY_USER_NOT_AUTHENTICATED) {
        return "KEY_USER_NOT_AUTHENTICATED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::OUTPUT_PARAMETER_NULL) {
        return "OUTPUT_PARAMETER_NULL";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_OPERATION_HANDLE) {
        return "INVALID_OPERATION_HANDLE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INSUFFICIENT_BUFFER_SPACE) {
        return "INSUFFICIENT_BUFFER_SPACE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::VERIFICATION_FAILED) {
        return "VERIFICATION_FAILED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::TOO_MANY_OPERATIONS) {
        return "TOO_MANY_OPERATIONS";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNEXPECTED_NULL_POINTER) {
        return "UNEXPECTED_NULL_POINTER";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_KEY_BLOB) {
        return "INVALID_KEY_BLOB";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_NOT_ENCRYPTED) {
        return "IMPORTED_KEY_NOT_ENCRYPTED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_DECRYPTION_FAILED) {
        return "IMPORTED_KEY_DECRYPTION_FAILED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_NOT_SIGNED) {
        return "IMPORTED_KEY_NOT_SIGNED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_VERIFICATION_FAILED) {
        return "IMPORTED_KEY_VERIFICATION_FAILED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_ARGUMENT) {
        return "INVALID_ARGUMENT";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_TAG) {
        return "UNSUPPORTED_TAG";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_TAG) {
        return "INVALID_TAG";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::MEMORY_ALLOCATION_FAILED) {
        return "MEMORY_ALLOCATION_FAILED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::IMPORT_PARAMETER_MISMATCH) {
        return "IMPORT_PARAMETER_MISMATCH";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_ACCESS_DENIED) {
        return "SECURE_HW_ACCESS_DENIED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::OPERATION_CANCELLED) {
        return "OPERATION_CANCELLED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::CONCURRENT_ACCESS_CONFLICT) {
        return "CONCURRENT_ACCESS_CONFLICT";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_BUSY) {
        return "SECURE_HW_BUSY";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_COMMUNICATION_FAILED) {
        return "SECURE_HW_COMMUNICATION_FAILED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_EC_FIELD) {
        return "UNSUPPORTED_EC_FIELD";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_NONCE) {
        return "MISSING_NONCE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_NONCE) {
        return "INVALID_NONCE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_MAC_LENGTH) {
        return "MISSING_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::KEY_RATE_LIMIT_EXCEEDED) {
        return "KEY_RATE_LIMIT_EXCEEDED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::CALLER_NONCE_PROHIBITED) {
        return "CALLER_NONCE_PROHIBITED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::KEY_MAX_OPS_EXCEEDED) {
        return "KEY_MAX_OPS_EXCEEDED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_MAC_LENGTH) {
        return "INVALID_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_MIN_MAC_LENGTH) {
        return "MISSING_MIN_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH) {
        return "UNSUPPORTED_MIN_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KDF) {
        return "UNSUPPORTED_KDF";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_EC_CURVE) {
        return "UNSUPPORTED_EC_CURVE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::KEY_REQUIRES_UPGRADE) {
        return "KEY_REQUIRES_UPGRADE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::ATTESTATION_CHALLENGE_MISSING) {
        return "ATTESTATION_CHALLENGE_MISSING";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::KEYMASTER_NOT_CONFIGURED) {
        return "KEYMASTER_NOT_CONFIGURED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::ATTESTATION_APPLICATION_ID_MISSING) {
        return "ATTESTATION_APPLICATION_ID_MISSING";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::CANNOT_ATTEST_IDS) {
        return "CANNOT_ATTEST_IDS";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::ROLLBACK_RESISTANCE_UNAVAILABLE) {
        return "ROLLBACK_RESISTANCE_UNAVAILABLE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::HARDWARE_TYPE_UNAVAILABLE) {
        return "HARDWARE_TYPE_UNAVAILABLE";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::PROOF_OF_PRESENCE_REQUIRED) {
        return "PROOF_OF_PRESENCE_REQUIRED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::CONCURRENT_PROOF_OF_PRESENCE_REQUESTED) {
        return "CONCURRENT_PROOF_OF_PRESENCE_REQUESTED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::NO_USER_CONFIRMATION) {
        return "NO_USER_CONFIRMATION";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::DEVICE_LOCKED) {
        return "DEVICE_LOCKED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNIMPLEMENTED) {
        return "UNIMPLEMENTED";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::VERSION_MISMATCH) {
        return "VERSION_MISMATCH";
    }
    if (o == ::android::hardware::keymaster::V4_0::ErrorCode::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::ErrorCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::KeyDerivationFunction>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::KeyDerivationFunction> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::KeyDerivationFunction::NONE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyDerivationFunction::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyDerivationFunction::NONE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyDerivationFunction::RFC5869_SHA256) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyDerivationFunction::RFC5869_SHA256)) {
        os += (first ? "" : " | ");
        os += "RFC5869_SHA256";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyDerivationFunction::RFC5869_SHA256;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA1) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA1)) {
        os += (first ? "" : " | ");
        os += "ISO18033_2_KDF1_SHA1";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA1;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA256) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA256)) {
        os += (first ? "" : " | ");
        os += "ISO18033_2_KDF1_SHA256";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA256;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA1) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA1)) {
        os += (first ? "" : " | ");
        os += "ISO18033_2_KDF2_SHA1";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA1;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA256) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA256)) {
        os += (first ? "" : " | ");
        os += "ISO18033_2_KDF2_SHA256";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA256;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::KeyDerivationFunction o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::KeyDerivationFunction::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyDerivationFunction::RFC5869_SHA256) {
        return "RFC5869_SHA256";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA1) {
        return "ISO18033_2_KDF1_SHA1";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA256) {
        return "ISO18033_2_KDF1_SHA256";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA1) {
        return "ISO18033_2_KDF2_SHA1";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA256) {
        return "ISO18033_2_KDF2_SHA256";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::KeyDerivationFunction o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::HardwareAuthenticatorType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::HardwareAuthenticatorType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::NONE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::NONE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::PASSWORD) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::PASSWORD)) {
        os += (first ? "" : " | ");
        os += "PASSWORD";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::PASSWORD;
    }
    if ((o & ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::FINGERPRINT) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::FINGERPRINT)) {
        os += (first ? "" : " | ");
        os += "FINGERPRINT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::FINGERPRINT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::ANY) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::ANY)) {
        os += (first ? "" : " | ");
        os += "ANY";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::ANY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::HardwareAuthenticatorType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::PASSWORD) {
        return "PASSWORD";
    }
    if (o == ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::FINGERPRINT) {
        return "FINGERPRINT";
    }
    if (o == ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::ANY) {
        return "ANY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::HardwareAuthenticatorType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::SecurityLevel>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::SecurityLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::SecurityLevel::SOFTWARE) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::SecurityLevel::SOFTWARE)) {
        os += (first ? "" : " | ");
        os += "SOFTWARE";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::SecurityLevel::SOFTWARE;
    }
    if ((o & ::android::hardware::keymaster::V4_0::SecurityLevel::TRUSTED_ENVIRONMENT) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::SecurityLevel::TRUSTED_ENVIRONMENT)) {
        os += (first ? "" : " | ");
        os += "TRUSTED_ENVIRONMENT";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::SecurityLevel::TRUSTED_ENVIRONMENT;
    }
    if ((o & ::android::hardware::keymaster::V4_0::SecurityLevel::STRONGBOX) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::SecurityLevel::STRONGBOX)) {
        os += (first ? "" : " | ");
        os += "STRONGBOX";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::SecurityLevel::STRONGBOX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::SecurityLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::SecurityLevel::SOFTWARE) {
        return "SOFTWARE";
    }
    if (o == ::android::hardware::keymaster::V4_0::SecurityLevel::TRUSTED_ENVIRONMENT) {
        return "TRUSTED_ENVIRONMENT";
    }
    if (o == ::android::hardware::keymaster::V4_0::SecurityLevel::STRONGBOX) {
        return "STRONGBOX";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::SecurityLevel o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V4_0::KeyFormat>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V4_0::KeyFormat> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V4_0::KeyFormat::X509) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyFormat::X509)) {
        os += (first ? "" : " | ");
        os += "X509";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyFormat::X509;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyFormat::PKCS8) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyFormat::PKCS8)) {
        os += (first ? "" : " | ");
        os += "PKCS8";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyFormat::PKCS8;
    }
    if ((o & ::android::hardware::keymaster::V4_0::KeyFormat::RAW) == static_cast<uint32_t>(::android::hardware::keymaster::V4_0::KeyFormat::RAW)) {
        os += (first ? "" : " | ");
        os += "RAW";
        first = false;
        flipped |= ::android::hardware::keymaster::V4_0::KeyFormat::RAW;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V4_0::KeyFormat o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V4_0::KeyFormat::X509) {
        return "X509";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyFormat::PKCS8) {
        return "PKCS8";
    }
    if (o == ::android::hardware::keymaster::V4_0::KeyFormat::RAW) {
        return "RAW";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V4_0::KeyFormat o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".algorithm = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.algorithm);
    os += ", .blockMode = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.blockMode);
    os += ", .paddingMode = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.paddingMode);
    os += ", .digest = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.digest);
    os += ", .ecCurve = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.ecCurve);
    os += ", .origin = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.origin);
    os += ", .keyBlobUsageRequirements = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.keyBlobUsageRequirements);
    os += ", .purpose = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.purpose);
    os += ", .keyDerivationFunction = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.keyDerivationFunction);
    os += ", .hardwareAuthenticatorType = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.hardwareAuthenticatorType);
    os += ", .hardwareType = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.hardwareType);
    os += ", .boolValue = ";
    os += ::android::hardware::toString(o.boolValue);
    os += ", .integer = ";
    os += ::android::hardware::toString(o.integer);
    os += ", .longInteger = ";
    os += ::android::hardware::toString(o.longInteger);
    os += ", .dateTime = ";
    os += ::android::hardware::toString(o.dateTime);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::keymaster::V4_0::KeyParameter::IntegerParams& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for IntegerParams

static inline std::string toString(const ::android::hardware::keymaster::V4_0::KeyParameter& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tag = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.tag);
    os += ", .f = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.f);
    os += ", .blob = ";
    os += ::android::hardware::toString(o.blob);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::keymaster::V4_0::KeyParameter& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for KeyParameter

static inline std::string toString(const ::android::hardware::keymaster::V4_0::KeyCharacteristics& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".softwareEnforced = ";
    os += ::android::hardware::toString(o.softwareEnforced);
    os += ", .hardwareEnforced = ";
    os += ::android::hardware::toString(o.hardwareEnforced);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::keymaster::V4_0::KeyCharacteristics& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for KeyCharacteristics

static inline std::string toString(const ::android::hardware::keymaster::V4_0::HardwareAuthToken& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".challenge = ";
    os += ::android::hardware::toString(o.challenge);
    os += ", .userId = ";
    os += ::android::hardware::toString(o.userId);
    os += ", .authenticatorId = ";
    os += ::android::hardware::toString(o.authenticatorId);
    os += ", .authenticatorType = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.authenticatorType);
    os += ", .timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += ", .mac = ";
    os += ::android::hardware::toString(o.mac);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::keymaster::V4_0::HardwareAuthToken& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::keymaster::V4_0::HardwareAuthToken& lhs, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& rhs) {
    if (lhs.challenge != rhs.challenge) {
        return false;
    }
    if (lhs.userId != rhs.userId) {
        return false;
    }
    if (lhs.authenticatorId != rhs.authenticatorId) {
        return false;
    }
    if (lhs.authenticatorType != rhs.authenticatorType) {
        return false;
    }
    if (lhs.timestamp != rhs.timestamp) {
        return false;
    }
    if (lhs.mac != rhs.mac) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::keymaster::V4_0::HardwareAuthToken& lhs, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::keymaster::V4_0::HmacSharingParameters& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".seed = ";
    os += ::android::hardware::toString(o.seed);
    os += ", .nonce = ";
    os += ::android::hardware::toString(o.nonce);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::keymaster::V4_0::HmacSharingParameters& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::keymaster::V4_0::HmacSharingParameters& lhs, const ::android::hardware::keymaster::V4_0::HmacSharingParameters& rhs) {
    if (lhs.seed != rhs.seed) {
        return false;
    }
    if (lhs.nonce != rhs.nonce) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::keymaster::V4_0::HmacSharingParameters& lhs, const ::android::hardware::keymaster::V4_0::HmacSharingParameters& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::keymaster::V4_0::VerificationToken& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".challenge = ";
    os += ::android::hardware::toString(o.challenge);
    os += ", .timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += ", .parametersVerified = ";
    os += ::android::hardware::toString(o.parametersVerified);
    os += ", .securityLevel = ";
    os += ::android::hardware::keymaster::V4_0::toString(o.securityLevel);
    os += ", .mac = ";
    os += ::android::hardware::toString(o.mac);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::keymaster::V4_0::VerificationToken& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for VerificationToken


}  // namespace V4_0
}  // namespace keymaster
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::Constants, 1> hidl_enum_values<::android::hardware::keymaster::V4_0::Constants> = {
    ::android::hardware::keymaster::V4_0::Constants::AUTH_TOKEN_MAC_LENGTH,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::TagType, 11> hidl_enum_values<::android::hardware::keymaster::V4_0::TagType> = {
    ::android::hardware::keymaster::V4_0::TagType::INVALID,
    ::android::hardware::keymaster::V4_0::TagType::ENUM,
    ::android::hardware::keymaster::V4_0::TagType::ENUM_REP,
    ::android::hardware::keymaster::V4_0::TagType::UINT,
    ::android::hardware::keymaster::V4_0::TagType::UINT_REP,
    ::android::hardware::keymaster::V4_0::TagType::ULONG,
    ::android::hardware::keymaster::V4_0::TagType::DATE,
    ::android::hardware::keymaster::V4_0::TagType::BOOL,
    ::android::hardware::keymaster::V4_0::TagType::BIGNUM,
    ::android::hardware::keymaster::V4_0::TagType::BYTES,
    ::android::hardware::keymaster::V4_0::TagType::ULONG_REP,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::Tag, 55> hidl_enum_values<::android::hardware::keymaster::V4_0::Tag> = {
    ::android::hardware::keymaster::V4_0::Tag::INVALID,
    ::android::hardware::keymaster::V4_0::Tag::PURPOSE,
    ::android::hardware::keymaster::V4_0::Tag::ALGORITHM,
    ::android::hardware::keymaster::V4_0::Tag::KEY_SIZE,
    ::android::hardware::keymaster::V4_0::Tag::BLOCK_MODE,
    ::android::hardware::keymaster::V4_0::Tag::DIGEST,
    ::android::hardware::keymaster::V4_0::Tag::PADDING,
    ::android::hardware::keymaster::V4_0::Tag::CALLER_NONCE,
    ::android::hardware::keymaster::V4_0::Tag::MIN_MAC_LENGTH,
    ::android::hardware::keymaster::V4_0::Tag::EC_CURVE,
    ::android::hardware::keymaster::V4_0::Tag::RSA_PUBLIC_EXPONENT,
    ::android::hardware::keymaster::V4_0::Tag::INCLUDE_UNIQUE_ID,
    ::android::hardware::keymaster::V4_0::Tag::BLOB_USAGE_REQUIREMENTS,
    ::android::hardware::keymaster::V4_0::Tag::BOOTLOADER_ONLY,
    ::android::hardware::keymaster::V4_0::Tag::ROLLBACK_RESISTANCE,
    ::android::hardware::keymaster::V4_0::Tag::HARDWARE_TYPE,
    ::android::hardware::keymaster::V4_0::Tag::ACTIVE_DATETIME,
    ::android::hardware::keymaster::V4_0::Tag::ORIGINATION_EXPIRE_DATETIME,
    ::android::hardware::keymaster::V4_0::Tag::USAGE_EXPIRE_DATETIME,
    ::android::hardware::keymaster::V4_0::Tag::MIN_SECONDS_BETWEEN_OPS,
    ::android::hardware::keymaster::V4_0::Tag::MAX_USES_PER_BOOT,
    ::android::hardware::keymaster::V4_0::Tag::USER_ID,
    ::android::hardware::keymaster::V4_0::Tag::USER_SECURE_ID,
    ::android::hardware::keymaster::V4_0::Tag::NO_AUTH_REQUIRED,
    ::android::hardware::keymaster::V4_0::Tag::USER_AUTH_TYPE,
    ::android::hardware::keymaster::V4_0::Tag::AUTH_TIMEOUT,
    ::android::hardware::keymaster::V4_0::Tag::ALLOW_WHILE_ON_BODY,
    ::android::hardware::keymaster::V4_0::Tag::TRUSTED_USER_PRESENCE_REQUIRED,
    ::android::hardware::keymaster::V4_0::Tag::TRUSTED_CONFIRMATION_REQUIRED,
    ::android::hardware::keymaster::V4_0::Tag::UNLOCKED_DEVICE_REQUIRED,
    ::android::hardware::keymaster::V4_0::Tag::APPLICATION_ID,
    ::android::hardware::keymaster::V4_0::Tag::APPLICATION_DATA,
    ::android::hardware::keymaster::V4_0::Tag::CREATION_DATETIME,
    ::android::hardware::keymaster::V4_0::Tag::ORIGIN,
    ::android::hardware::keymaster::V4_0::Tag::ROOT_OF_TRUST,
    ::android::hardware::keymaster::V4_0::Tag::OS_VERSION,
    ::android::hardware::keymaster::V4_0::Tag::OS_PATCHLEVEL,
    ::android::hardware::keymaster::V4_0::Tag::UNIQUE_ID,
    ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_CHALLENGE,
    ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_APPLICATION_ID,
    ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_BRAND,
    ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_DEVICE,
    ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_PRODUCT,
    ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_SERIAL,
    ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_IMEI,
    ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MEID,
    ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MANUFACTURER,
    ::android::hardware::keymaster::V4_0::Tag::ATTESTATION_ID_MODEL,
    ::android::hardware::keymaster::V4_0::Tag::VENDOR_PATCHLEVEL,
    ::android::hardware::keymaster::V4_0::Tag::BOOT_PATCHLEVEL,
    ::android::hardware::keymaster::V4_0::Tag::ASSOCIATED_DATA,
    ::android::hardware::keymaster::V4_0::Tag::NONCE,
    ::android::hardware::keymaster::V4_0::Tag::MAC_LENGTH,
    ::android::hardware::keymaster::V4_0::Tag::RESET_SINCE_ID_ROTATION,
    ::android::hardware::keymaster::V4_0::Tag::CONFIRMATION_TOKEN,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::Algorithm, 5> hidl_enum_values<::android::hardware::keymaster::V4_0::Algorithm> = {
    ::android::hardware::keymaster::V4_0::Algorithm::RSA,
    ::android::hardware::keymaster::V4_0::Algorithm::EC,
    ::android::hardware::keymaster::V4_0::Algorithm::AES,
    ::android::hardware::keymaster::V4_0::Algorithm::TRIPLE_DES,
    ::android::hardware::keymaster::V4_0::Algorithm::HMAC,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::BlockMode, 4> hidl_enum_values<::android::hardware::keymaster::V4_0::BlockMode> = {
    ::android::hardware::keymaster::V4_0::BlockMode::ECB,
    ::android::hardware::keymaster::V4_0::BlockMode::CBC,
    ::android::hardware::keymaster::V4_0::BlockMode::CTR,
    ::android::hardware::keymaster::V4_0::BlockMode::GCM,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::PaddingMode, 6> hidl_enum_values<::android::hardware::keymaster::V4_0::PaddingMode> = {
    ::android::hardware::keymaster::V4_0::PaddingMode::NONE,
    ::android::hardware::keymaster::V4_0::PaddingMode::RSA_OAEP,
    ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PSS,
    ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PKCS1_1_5_ENCRYPT,
    ::android::hardware::keymaster::V4_0::PaddingMode::RSA_PKCS1_1_5_SIGN,
    ::android::hardware::keymaster::V4_0::PaddingMode::PKCS7,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::Digest, 7> hidl_enum_values<::android::hardware::keymaster::V4_0::Digest> = {
    ::android::hardware::keymaster::V4_0::Digest::NONE,
    ::android::hardware::keymaster::V4_0::Digest::MD5,
    ::android::hardware::keymaster::V4_0::Digest::SHA1,
    ::android::hardware::keymaster::V4_0::Digest::SHA_2_224,
    ::android::hardware::keymaster::V4_0::Digest::SHA_2_256,
    ::android::hardware::keymaster::V4_0::Digest::SHA_2_384,
    ::android::hardware::keymaster::V4_0::Digest::SHA_2_512,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::EcCurve, 4> hidl_enum_values<::android::hardware::keymaster::V4_0::EcCurve> = {
    ::android::hardware::keymaster::V4_0::EcCurve::P_224,
    ::android::hardware::keymaster::V4_0::EcCurve::P_256,
    ::android::hardware::keymaster::V4_0::EcCurve::P_384,
    ::android::hardware::keymaster::V4_0::EcCurve::P_521,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::KeyOrigin, 5> hidl_enum_values<::android::hardware::keymaster::V4_0::KeyOrigin> = {
    ::android::hardware::keymaster::V4_0::KeyOrigin::GENERATED,
    ::android::hardware::keymaster::V4_0::KeyOrigin::DERIVED,
    ::android::hardware::keymaster::V4_0::KeyOrigin::IMPORTED,
    ::android::hardware::keymaster::V4_0::KeyOrigin::UNKNOWN,
    ::android::hardware::keymaster::V4_0::KeyOrigin::SECURELY_IMPORTED,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements, 2> hidl_enum_values<::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements> = {
    ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements::STANDALONE,
    ::android::hardware::keymaster::V4_0::KeyBlobUsageRequirements::REQUIRES_FILE_SYSTEM,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::KeyPurpose, 5> hidl_enum_values<::android::hardware::keymaster::V4_0::KeyPurpose> = {
    ::android::hardware::keymaster::V4_0::KeyPurpose::ENCRYPT,
    ::android::hardware::keymaster::V4_0::KeyPurpose::DECRYPT,
    ::android::hardware::keymaster::V4_0::KeyPurpose::SIGN,
    ::android::hardware::keymaster::V4_0::KeyPurpose::VERIFY,
    ::android::hardware::keymaster::V4_0::KeyPurpose::WRAP_KEY,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::ErrorCode, 74> hidl_enum_values<::android::hardware::keymaster::V4_0::ErrorCode> = {
    ::android::hardware::keymaster::V4_0::ErrorCode::OK,
    ::android::hardware::keymaster::V4_0::ErrorCode::ROOT_OF_TRUST_ALREADY_SET,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_PURPOSE,
    ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_PURPOSE,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_ALGORITHM,
    ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_ALGORITHM,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_SIZE,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_BLOCK_MODE,
    ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_BLOCK_MODE,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_MAC_LENGTH,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_PADDING_MODE,
    ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_PADDING_MODE,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_DIGEST,
    ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_DIGEST,
    ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_EXPIRATION_TIME,
    ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_USER_ID,
    ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_AUTHORIZATION_TIMEOUT,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_FORMAT,
    ::android::hardware::keymaster::V4_0::ErrorCode::INCOMPATIBLE_KEY_FORMAT,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KEY_VERIFICATION_ALGORITHM,
    ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_INPUT_LENGTH,
    ::android::hardware::keymaster::V4_0::ErrorCode::KEY_EXPORT_OPTIONS_INVALID,
    ::android::hardware::keymaster::V4_0::ErrorCode::DELEGATION_NOT_ALLOWED,
    ::android::hardware::keymaster::V4_0::ErrorCode::KEY_NOT_YET_VALID,
    ::android::hardware::keymaster::V4_0::ErrorCode::KEY_EXPIRED,
    ::android::hardware::keymaster::V4_0::ErrorCode::KEY_USER_NOT_AUTHENTICATED,
    ::android::hardware::keymaster::V4_0::ErrorCode::OUTPUT_PARAMETER_NULL,
    ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_OPERATION_HANDLE,
    ::android::hardware::keymaster::V4_0::ErrorCode::INSUFFICIENT_BUFFER_SPACE,
    ::android::hardware::keymaster::V4_0::ErrorCode::VERIFICATION_FAILED,
    ::android::hardware::keymaster::V4_0::ErrorCode::TOO_MANY_OPERATIONS,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNEXPECTED_NULL_POINTER,
    ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_KEY_BLOB,
    ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_NOT_ENCRYPTED,
    ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_DECRYPTION_FAILED,
    ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_NOT_SIGNED,
    ::android::hardware::keymaster::V4_0::ErrorCode::IMPORTED_KEY_VERIFICATION_FAILED,
    ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_ARGUMENT,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_TAG,
    ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_TAG,
    ::android::hardware::keymaster::V4_0::ErrorCode::MEMORY_ALLOCATION_FAILED,
    ::android::hardware::keymaster::V4_0::ErrorCode::IMPORT_PARAMETER_MISMATCH,
    ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_ACCESS_DENIED,
    ::android::hardware::keymaster::V4_0::ErrorCode::OPERATION_CANCELLED,
    ::android::hardware::keymaster::V4_0::ErrorCode::CONCURRENT_ACCESS_CONFLICT,
    ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_BUSY,
    ::android::hardware::keymaster::V4_0::ErrorCode::SECURE_HW_COMMUNICATION_FAILED,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_EC_FIELD,
    ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_NONCE,
    ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_NONCE,
    ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_MAC_LENGTH,
    ::android::hardware::keymaster::V4_0::ErrorCode::KEY_RATE_LIMIT_EXCEEDED,
    ::android::hardware::keymaster::V4_0::ErrorCode::CALLER_NONCE_PROHIBITED,
    ::android::hardware::keymaster::V4_0::ErrorCode::KEY_MAX_OPS_EXCEEDED,
    ::android::hardware::keymaster::V4_0::ErrorCode::INVALID_MAC_LENGTH,
    ::android::hardware::keymaster::V4_0::ErrorCode::MISSING_MIN_MAC_LENGTH,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_KDF,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNSUPPORTED_EC_CURVE,
    ::android::hardware::keymaster::V4_0::ErrorCode::KEY_REQUIRES_UPGRADE,
    ::android::hardware::keymaster::V4_0::ErrorCode::ATTESTATION_CHALLENGE_MISSING,
    ::android::hardware::keymaster::V4_0::ErrorCode::KEYMASTER_NOT_CONFIGURED,
    ::android::hardware::keymaster::V4_0::ErrorCode::ATTESTATION_APPLICATION_ID_MISSING,
    ::android::hardware::keymaster::V4_0::ErrorCode::CANNOT_ATTEST_IDS,
    ::android::hardware::keymaster::V4_0::ErrorCode::ROLLBACK_RESISTANCE_UNAVAILABLE,
    ::android::hardware::keymaster::V4_0::ErrorCode::HARDWARE_TYPE_UNAVAILABLE,
    ::android::hardware::keymaster::V4_0::ErrorCode::PROOF_OF_PRESENCE_REQUIRED,
    ::android::hardware::keymaster::V4_0::ErrorCode::CONCURRENT_PROOF_OF_PRESENCE_REQUESTED,
    ::android::hardware::keymaster::V4_0::ErrorCode::NO_USER_CONFIRMATION,
    ::android::hardware::keymaster::V4_0::ErrorCode::DEVICE_LOCKED,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNIMPLEMENTED,
    ::android::hardware::keymaster::V4_0::ErrorCode::VERSION_MISMATCH,
    ::android::hardware::keymaster::V4_0::ErrorCode::UNKNOWN_ERROR,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::KeyDerivationFunction, 6> hidl_enum_values<::android::hardware::keymaster::V4_0::KeyDerivationFunction> = {
    ::android::hardware::keymaster::V4_0::KeyDerivationFunction::NONE,
    ::android::hardware::keymaster::V4_0::KeyDerivationFunction::RFC5869_SHA256,
    ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA1,
    ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA256,
    ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA1,
    ::android::hardware::keymaster::V4_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA256,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::HardwareAuthenticatorType, 4> hidl_enum_values<::android::hardware::keymaster::V4_0::HardwareAuthenticatorType> = {
    ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::NONE,
    ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::PASSWORD,
    ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::FINGERPRINT,
    ::android::hardware::keymaster::V4_0::HardwareAuthenticatorType::ANY,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::SecurityLevel, 3> hidl_enum_values<::android::hardware::keymaster::V4_0::SecurityLevel> = {
    ::android::hardware::keymaster::V4_0::SecurityLevel::SOFTWARE,
    ::android::hardware::keymaster::V4_0::SecurityLevel::TRUSTED_ENVIRONMENT,
    ::android::hardware::keymaster::V4_0::SecurityLevel::STRONGBOX,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V4_0::KeyFormat, 3> hidl_enum_values<::android::hardware::keymaster::V4_0::KeyFormat> = {
    ::android::hardware::keymaster::V4_0::KeyFormat::X509,
    ::android::hardware::keymaster::V4_0::KeyFormat::PKCS8,
    ::android::hardware::keymaster::V4_0::KeyFormat::RAW,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_0_TYPES_H
