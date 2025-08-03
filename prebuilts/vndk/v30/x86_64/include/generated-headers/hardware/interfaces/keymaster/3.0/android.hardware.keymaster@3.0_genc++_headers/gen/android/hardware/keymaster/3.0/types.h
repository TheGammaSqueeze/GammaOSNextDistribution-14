#ifndef HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V3_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V3_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace keymaster {
namespace V3_0 {

// Forward declaration for forward reference support:
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
enum class ErrorCode : uint32_t;
enum class KeyDerivationFunction : uint32_t;
enum class HardwareAuthenticatorType : uint32_t;
struct KeyParameter;
struct KeyCharacteristics;
struct HardwareAuthToken;
enum class SecurityLevel : uint32_t;
enum class KeyFormat : uint32_t;

enum class TagType : uint32_t {
    INVALID = 0u /* 0 << 28 */,
    /*
     * Invalid type, used to designate a tag as uninitialized
     */
    ENUM = 268435456u /* 1 << 28 */,
    ENUM_REP = 536870912u /* 2 << 28 */,
    /*
     * Repeatable enumeration value.
     */
    UINT = 805306368u /* 3 << 28 */,
    UINT_REP = 1073741824u /* 4 << 28 */,
    /*
     * Repeatable integer value
     */
    ULONG = 1342177280u /* 5 << 28 */,
    DATE = 1610612736u /* 6 << 28 */,
    BOOL = 1879048192u /* 7 << 28 */,
    BIGNUM = 2147483648u /* 8 << 28 */,
    BYTES = 2415919104u /* 9 << 28 */,
    ULONG_REP = 2684354560u /* 10 << 28 */,
};

enum class Tag : uint32_t {
    INVALID = 0u /* TagType:INVALID | 0 */,
    /**
     * Tags that must be semantically enforced by hardware and software implementations.
     *
     *
     * Crypto parameters
     */
    PURPOSE = 536870913u /* TagType:ENUM_REP | 1 */,
    /**
     * KeyPurpose.
     */
    ALGORITHM = 268435458u /* TagType:ENUM | 2 */,
    /**
     * Algorithm.
     */
    KEY_SIZE = 805306371u /* TagType:UINT | 3 */,
    /**
     * Key size in bits.
     */
    BLOCK_MODE = 536870916u /* TagType:ENUM_REP | 4 */,
    /**
     * BlockMode.
     */
    DIGEST = 536870917u /* TagType:ENUM_REP | 5 */,
    /**
     * Digest.
     */
    PADDING = 536870918u /* TagType:ENUM_REP | 6 */,
    /**
     * PaddingMode.
     */
    CALLER_NONCE = 1879048199u /* TagType:BOOL | 7 */,
    /**
     * Allow caller to specify nonce or IV.
     */
    MIN_MAC_LENGTH = 805306376u /* TagType:UINT | 8 */,
    /*
     * Minimum length of MAC or AEAD authentication tag in
     * bits.
     */
    KDF = 536870921u /* TagType:ENUM_REP | 9 */,
    /**
     * KeyDerivationFunction.
     */
    EC_CURVE = 268435466u /* TagType:ENUM | 10 */,
    /**
     * EcCurve.
     *
     *
     * Algorithm-specific.
     */
    RSA_PUBLIC_EXPONENT = 1342177480u /* TagType:ULONG | 200 */,
    ECIES_SINGLE_HASH_MODE = 1879048393u /* TagType:BOOL | 201 */,
    /*
     * Whether the ephemeral public key is fed into the
     * KDF.
     */
    INCLUDE_UNIQUE_ID = 1879048394u /* TagType:BOOL | 202 */,
    /*
     * If true, attestation certificates for this key
     * will contain an application-scoped and
     * time-bounded device-unique ID.
     *
     *
     * Other hardware-enforced.
     */
    BLOB_USAGE_REQUIREMENTS = 268435757u /* TagType:ENUM | 301 */,
    /**
     * KeyBlobUsageRequirements.
     */
    BOOTLOADER_ONLY = 1879048494u /* TagType:BOOL | 302 */,
    /**
     * Usable only by bootloader.
     *
     *
     * Tags that should be semantically enforced by hardware if possible and will otherwise be
     * enforced by software (keystore).
     *
     *
     * Key validity period
     */
    ACTIVE_DATETIME = 1610613136u /* TagType:DATE | 400 */,
    /**
     * Start of validity.
     */
    ORIGINATION_EXPIRE_DATETIME = 1610613137u /* TagType:DATE | 401 */,
    /*
     * Date when new "messages" should no longer
     * be created.
     */
    USAGE_EXPIRE_DATETIME = 1610613138u /* TagType:DATE | 402 */,
    /*
     * Date when existing "messages" should no
     * longer be trusted.
     */
    MIN_SECONDS_BETWEEN_OPS = 805306771u /* TagType:UINT | 403 */,
    /*
     * Minimum elapsed time between
     * cryptographic operations with the key.
     */
    MAX_USES_PER_BOOT = 805306772u /* TagType:UINT | 404 */,
    /*
     * Number of times the key can be used per
     * boot.
     *
     *
     * User authentication
     */
    ALL_USERS = 1879048692u /* TagType:BOOL | 500 */,
    /**
     * Reserved for future use -- ignore.
     */
    USER_ID = 805306869u /* TagType:UINT | 501 */,
    /**
     * Reserved for future use -- ignore.
     */
    USER_SECURE_ID = 2684355062u /* TagType:ULONG_REP | 502 */,
    /*
     * Secure ID of authorized user or authenticator(s).
     * Disallowed if ALL_USERS or NO_AUTH_REQUIRED is
     * present.
     */
    NO_AUTH_REQUIRED = 1879048695u /* TagType:BOOL | 503 */,
    /**
     * If key is usable without authentication.
     */
    USER_AUTH_TYPE = 268435960u /* TagType:ENUM | 504 */,
    /*
     * Bitmask of authenticator types allowed when
     * USER_SECURE_ID contains a secure user ID, rather
     * than a secure authenticator ID.  Defined in
     * HardwareAuthenticatorType.
     */
    AUTH_TIMEOUT = 805306873u /* TagType:UINT | 505 */,
    /*
     * Required freshness of user authentication for
     * private/secret key operations, in seconds.  Public
     * key operations require no authentication.  If
     * absent, authentication is required for every use.
     * Authentication state is lost when the device is
     * powered off.
     */
    ALLOW_WHILE_ON_BODY = 1879048698u /* TagType:BOOL | 506 */,
    /*
     * Allow key to be used after authentication timeout
     * if device is still on-body (requires secure on-body
     * sensor.
     *
     *
     * Application access control
     */
    ALL_APPLICATIONS = 1879048792u /* TagType:BOOL | 600 */,
    /*
     * Specified to indicate key is usable by all
     * applications.
     */
    APPLICATION_ID = 2415919705u /* TagType:BYTES | 601 */,
    /**
     * Byte string identifying the authorized application.
     */
    EXPORTABLE = 1879048794u /* TagType:BOOL | 602 */,
    /*
     * If true, private/secret key can be exported, but only
     * if all access control requirements for use are
     * met. (keymaster2)
     *
     *
     * Semantically unenforceable tags, either because they have no specific meaning or because
     * they're informational only.
     */
    APPLICATION_DATA = 2415919804u /* TagType:BYTES | 700 */,
    /**
     * Data provided by authorized application.
     */
    CREATION_DATETIME = 1610613437u /* TagType:DATE | 701 */,
    /**
     * Key creation time
     */
    ORIGIN = 268436158u /* TagType:ENUM | 702 */,
    /**
     * keymaster_key_origin_t.
     */
    ROLLBACK_RESISTANT = 1879048895u /* TagType:BOOL | 703 */,
    /**
     * Whether key is rollback-resistant.
     */
    ROOT_OF_TRUST = 2415919808u /* TagType:BYTES | 704 */,
    /**
     * Root of trust ID.
     */
    OS_VERSION = 805307073u /* TagType:UINT | 705 */,
    /**
     * Version of system (keymaster2)
     */
    OS_PATCHLEVEL = 805307074u /* TagType:UINT | 706 */,
    /**
     * Patch level of system (keymaster2)
     */
    UNIQUE_ID = 2415919811u /* TagType:BYTES | 707 */,
    /**
     * Used to provide unique ID in attestation
     */
    ATTESTATION_CHALLENGE = 2415919812u /* TagType:BYTES | 708 */,
    /**
     * Used to provide challenge in attestation
     */
    ATTESTATION_APPLICATION_ID = 2415919813u /* TagType:BYTES | 709 */,
    /*
     * Used to identify the set of possible
     * applications of which one has initiated a
     * key attestation
     */
    ATTESTATION_ID_BRAND = 2415919814u /* TagType:BYTES | 710 */,
    /*
     * Used to provide the device's brand name to be
     * included in attestation
     */
    ATTESTATION_ID_DEVICE = 2415919815u /* TagType:BYTES | 711 */,
    /*
     * Used to provide the device's device name to be
     * included in attestation
     */
    ATTESTATION_ID_PRODUCT = 2415919816u /* TagType:BYTES | 712 */,
    /*
     * Used to provide the device's product name to be
     * included in attestation
     */
    ATTESTATION_ID_SERIAL = 2415919817u /* TagType:BYTES | 713 */,
    /*
     * Used to provide the device's serial number to be
     * included in attestation
     */
    ATTESTATION_ID_IMEI = 2415919818u /* TagType:BYTES | 714 */,
    /*
     * Used to provide the device's IMEI to be included
     * in attestation
     */
    ATTESTATION_ID_MEID = 2415919819u /* TagType:BYTES | 715 */,
    /*
     * Used to provide the device's MEID to be included
     * in attestation
     */
    ATTESTATION_ID_MANUFACTURER = 2415919820u /* TagType:BYTES | 716 */,
    /*
     * Used to provide the device's manufacturer
     * name to be included in attestation
     */
    ATTESTATION_ID_MODEL = 2415919821u /* TagType:BYTES | 717 */,
    /*
     * Used to provide the device's model name to be
     * included in attestation
     *
     *
     * Tags used only to provide data to or receive data from operations
     */
    ASSOCIATED_DATA = 2415920104u /* TagType:BYTES | 1000 */,
    /**
     * Used to provide associated data for AEAD modes.
     */
    NONCE = 2415920105u /* TagType:BYTES | 1001 */,
    /**
     * Nonce or Initialization Vector
     */
    AUTH_TOKEN = 2415920106u /* TagType:BYTES | 1002 */,
    /*
     * Authentication token that proves secure user
     * authentication has been performed.  Structure defined
     * in hw_auth_token_t in hw_auth_token.h.
     */
    MAC_LENGTH = 805307371u /* TagType:UINT | 1003 */,
    /**
     * MAC or AEAD authentication tag length in bits.
     */
    RESET_SINCE_ID_ROTATION = 1879049196u /* TagType:BOOL | 1004 */,
};

enum class Algorithm : uint32_t {
    /**
     * Asymmetric algorithms.
     */
    RSA = 1u,
    EC = 3u,
    /**
     * Block ciphers algorithms
     */
    AES = 32u,
    /**
     * MAC algorithms
     */
    HMAC = 128u,
};

/**
 * Symmetric block cipher modes provided by keymaster implementations.
 */
enum class BlockMode : uint32_t {
    /**
     * Unauthenticated modes, usable only for encryption/decryption and not generally recommended
     * except for compatibility with existing other protocols.
     */
    ECB = 1u,
    CBC = 2u,
    CTR = 3u,
    /**
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
    /**
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
    /*
     * Optional, may not be implemented in hardware, will be handled in software if
     * needed.
     */
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
 * hardware or software-based.  Specifically, a key with GENERATED in the hardware-enforced list is
 * guaranteed never to have existed outide the secure hardware.
 */
enum class KeyOrigin : uint32_t {
    GENERATED = 0u,
    /**
     * Generated in keymaster.  Should not exist outside the TEE.
     */
    DERIVED = 1u,
    /**
     * Derived inside keymaster.  Likely exists off-device.
     */
    IMPORTED = 2u,
    /**
     * Imported into keymaster.  Existed as cleartext in Android.
     */
    UNKNOWN = 3u,
};

/**
 * Usability requirements of key blobs.  This defines what system functionality must be available
 * for the key to function.  For example, key "blobs" which are actually handles referencing
 * encrypted key material stored in the file system cannot be used until the file system is
 * available, and should have BLOB_REQUIRES_FILE_SYSTEM.  Other requirements entries will be added
 * as needed for implementations.
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
     */
    DERIVE_KEY = 4u,
    /*
     * Usable with EC keys.
     */
    WRAP_KEY = 5u,
};

/**
 * Keymaster error codes.
 */
enum class ErrorCode : uint32_t {
    OK = 0u,
    ROOT_OF_TRUST_ALREADY_SET = 4294967295u /* -1 */,
    UNSUPPORTED_PURPOSE = 4294967294u /* -2 */,
    INCOMPATIBLE_PURPOSE = 4294967293u /* -3 */,
    UNSUPPORTED_ALGORITHM = 4294967292u /* -4 */,
    INCOMPATIBLE_ALGORITHM = 4294967291u /* -5 */,
    UNSUPPORTED_KEY_SIZE = 4294967290u /* -6 */,
    UNSUPPORTED_BLOCK_MODE = 4294967289u /* -7 */,
    INCOMPATIBLE_BLOCK_MODE = 4294967288u /* -8 */,
    UNSUPPORTED_MAC_LENGTH = 4294967287u /* -9 */,
    UNSUPPORTED_PADDING_MODE = 4294967286u /* -10 */,
    INCOMPATIBLE_PADDING_MODE = 4294967285u /* -11 */,
    UNSUPPORTED_DIGEST = 4294967284u /* -12 */,
    INCOMPATIBLE_DIGEST = 4294967283u /* -13 */,
    INVALID_EXPIRATION_TIME = 4294967282u /* -14 */,
    INVALID_USER_ID = 4294967281u /* -15 */,
    INVALID_AUTHORIZATION_TIMEOUT = 4294967280u /* -16 */,
    UNSUPPORTED_KEY_FORMAT = 4294967279u /* -17 */,
    INCOMPATIBLE_KEY_FORMAT = 4294967278u /* -18 */,
    UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM = 4294967277u /* -19 */,
    /**
     * For PKCS8 & PKCS12
     */
    UNSUPPORTED_KEY_VERIFICATION_ALGORITHM = 4294967276u /* -20 */,
    /**
     * For PKCS8 & PKCS12
     */
    INVALID_INPUT_LENGTH = 4294967275u /* -21 */,
    KEY_EXPORT_OPTIONS_INVALID = 4294967274u /* -22 */,
    DELEGATION_NOT_ALLOWED = 4294967273u /* -23 */,
    KEY_NOT_YET_VALID = 4294967272u /* -24 */,
    KEY_EXPIRED = 4294967271u /* -25 */,
    KEY_USER_NOT_AUTHENTICATED = 4294967270u /* -26 */,
    OUTPUT_PARAMETER_NULL = 4294967269u /* -27 */,
    INVALID_OPERATION_HANDLE = 4294967268u /* -28 */,
    INSUFFICIENT_BUFFER_SPACE = 4294967267u /* -29 */,
    VERIFICATION_FAILED = 4294967266u /* -30 */,
    TOO_MANY_OPERATIONS = 4294967265u /* -31 */,
    UNEXPECTED_NULL_POINTER = 4294967264u /* -32 */,
    INVALID_KEY_BLOB = 4294967263u /* -33 */,
    IMPORTED_KEY_NOT_ENCRYPTED = 4294967262u /* -34 */,
    IMPORTED_KEY_DECRYPTION_FAILED = 4294967261u /* -35 */,
    IMPORTED_KEY_NOT_SIGNED = 4294967260u /* -36 */,
    IMPORTED_KEY_VERIFICATION_FAILED = 4294967259u /* -37 */,
    INVALID_ARGUMENT = 4294967258u /* -38 */,
    UNSUPPORTED_TAG = 4294967257u /* -39 */,
    INVALID_TAG = 4294967256u /* -40 */,
    MEMORY_ALLOCATION_FAILED = 4294967255u /* -41 */,
    IMPORT_PARAMETER_MISMATCH = 4294967252u /* -44 */,
    SECURE_HW_ACCESS_DENIED = 4294967251u /* -45 */,
    OPERATION_CANCELLED = 4294967250u /* -46 */,
    CONCURRENT_ACCESS_CONFLICT = 4294967249u /* -47 */,
    SECURE_HW_BUSY = 4294967248u /* -48 */,
    SECURE_HW_COMMUNICATION_FAILED = 4294967247u /* -49 */,
    UNSUPPORTED_EC_FIELD = 4294967246u /* -50 */,
    MISSING_NONCE = 4294967245u /* -51 */,
    INVALID_NONCE = 4294967244u /* -52 */,
    MISSING_MAC_LENGTH = 4294967243u /* -53 */,
    KEY_RATE_LIMIT_EXCEEDED = 4294967242u /* -54 */,
    CALLER_NONCE_PROHIBITED = 4294967241u /* -55 */,
    KEY_MAX_OPS_EXCEEDED = 4294967240u /* -56 */,
    INVALID_MAC_LENGTH = 4294967239u /* -57 */,
    MISSING_MIN_MAC_LENGTH = 4294967238u /* -58 */,
    UNSUPPORTED_MIN_MAC_LENGTH = 4294967237u /* -59 */,
    UNSUPPORTED_KDF = 4294967236u /* -60 */,
    UNSUPPORTED_EC_CURVE = 4294967235u /* -61 */,
    KEY_REQUIRES_UPGRADE = 4294967234u /* -62 */,
    ATTESTATION_CHALLENGE_MISSING = 4294967233u /* -63 */,
    KEYMASTER_NOT_CONFIGURED = 4294967232u /* -64 */,
    ATTESTATION_APPLICATION_ID_MISSING = 4294967231u /* -65 */,
    CANNOT_ATTEST_IDS = 4294967230u /* -66 */,
    UNIMPLEMENTED = 4294967196u /* -100 */,
    VERSION_MISMATCH = 4294967195u /* -101 */,
    UNKNOWN_ERROR = 4294966296u /* -1000 */,
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

struct KeyParameter final {
    // Forward declaration for forward reference support:
    union IntegerParams;

    union IntegerParams final {
        /**
         * Enum types
         */
        ::android::hardware::keymaster::V3_0::Algorithm algorithm __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V3_0::BlockMode blockMode __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V3_0::PaddingMode paddingMode __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V3_0::Digest digest __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V3_0::EcCurve ecCurve __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V3_0::KeyOrigin origin __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements keyBlobUsageRequirements __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V3_0::KeyPurpose purpose __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V3_0::KeyDerivationFunction keyDerivationFunction __attribute__ ((aligned(4)));
        ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType hardwareAuthenticatorType __attribute__ ((aligned(4)));
        /**
         * Other types
         */
        bool boolValue __attribute__ ((aligned(1)));
        uint32_t integer __attribute__ ((aligned(4)));
        uint64_t longInteger __attribute__ ((aligned(8)));
        uint64_t dateTime __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, algorithm) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, blockMode) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, paddingMode) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, digest) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, ecCurve) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, origin) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, keyBlobUsageRequirements) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, purpose) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, keyDerivationFunction) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, hardwareAuthenticatorType) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, boolValue) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, integer) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, longInteger) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams, dateTime) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams) == 8, "wrong alignment");

    /**
     * Discriminates the uinon/blob field used.  The blob cannot be coincided with the union, but
     * only one of "f" and "blob" is ever used at a time.
     */
    ::android::hardware::keymaster::V3_0::Tag tag __attribute__ ((aligned(4)));
    ::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams f __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<uint8_t> blob __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter, tag) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter, f) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyParameter, blob) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::keymaster::V3_0::KeyParameter) == 32, "wrong size");
static_assert(__alignof(::android::hardware::keymaster::V3_0::KeyParameter) == 8, "wrong alignment");

struct KeyCharacteristics final {
    ::android::hardware::hidl_vec<::android::hardware::keymaster::V3_0::KeyParameter> softwareEnforced __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<::android::hardware::keymaster::V3_0::KeyParameter> teeEnforced __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyCharacteristics, softwareEnforced) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V3_0::KeyCharacteristics, teeEnforced) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::keymaster::V3_0::KeyCharacteristics) == 32, "wrong size");
static_assert(__alignof(::android::hardware::keymaster::V3_0::KeyCharacteristics) == 8, "wrong alignment");

/**
 * Data used to prove successful authentication.
 */
struct HardwareAuthToken final {
    uint64_t challenge __attribute__ ((aligned(8)));
    uint64_t userId __attribute__ ((aligned(8)));
    uint64_t authenticatorId __attribute__ ((aligned(8)));
    uint32_t authenticatorType __attribute__ ((aligned(4)));
    uint64_t timestamp __attribute__ ((aligned(8)));
    ::android::hardware::hidl_array<uint8_t, 32> hmac __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::keymaster::V3_0::HardwareAuthToken, challenge) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V3_0::HardwareAuthToken, userId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V3_0::HardwareAuthToken, authenticatorId) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V3_0::HardwareAuthToken, authenticatorType) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V3_0::HardwareAuthToken, timestamp) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::keymaster::V3_0::HardwareAuthToken, hmac) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::keymaster::V3_0::HardwareAuthToken) == 72, "wrong size");
static_assert(__alignof(::android::hardware::keymaster::V3_0::HardwareAuthToken) == 8, "wrong alignment");

enum class SecurityLevel : uint32_t {
    SOFTWARE = 0u,
    TRUSTED_ENVIRONMENT = 1u,
};

/**
 * Formats for key import and export.
 */
enum class KeyFormat : uint32_t {
    X509 = 0u,
    /**
     * for public key export
     */
    PKCS8 = 1u,
    /**
     * for asymmetric key pair import
     */
    RAW = 3u,
};

typedef uint64_t OperationHandle;

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::TagType o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::TagType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::TagType lhs, const ::android::hardware::keymaster::V3_0::TagType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::TagType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::TagType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::TagType lhs, const ::android::hardware::keymaster::V3_0::TagType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::TagType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::TagType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::TagType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::TagType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::Tag o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::Tag o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::Tag lhs, const ::android::hardware::keymaster::V3_0::Tag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::Tag rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::Tag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::Tag lhs, const ::android::hardware::keymaster::V3_0::Tag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::Tag rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::Tag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::Tag e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::Tag e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::Algorithm o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::Algorithm o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::Algorithm lhs, const ::android::hardware::keymaster::V3_0::Algorithm rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::Algorithm rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::Algorithm lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::Algorithm lhs, const ::android::hardware::keymaster::V3_0::Algorithm rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::Algorithm rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::Algorithm lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::Algorithm e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::Algorithm e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::BlockMode o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::BlockMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::BlockMode lhs, const ::android::hardware::keymaster::V3_0::BlockMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::BlockMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::BlockMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::BlockMode lhs, const ::android::hardware::keymaster::V3_0::BlockMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::BlockMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::BlockMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::BlockMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::BlockMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::PaddingMode o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::PaddingMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::PaddingMode lhs, const ::android::hardware::keymaster::V3_0::PaddingMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::PaddingMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::PaddingMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::PaddingMode lhs, const ::android::hardware::keymaster::V3_0::PaddingMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::PaddingMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::PaddingMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::PaddingMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::PaddingMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::Digest o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::Digest o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::Digest lhs, const ::android::hardware::keymaster::V3_0::Digest rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::Digest rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::Digest lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::Digest lhs, const ::android::hardware::keymaster::V3_0::Digest rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::Digest rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::Digest lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::Digest e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::Digest e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::EcCurve o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::EcCurve o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::EcCurve lhs, const ::android::hardware::keymaster::V3_0::EcCurve rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::EcCurve rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::EcCurve lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::EcCurve lhs, const ::android::hardware::keymaster::V3_0::EcCurve rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::EcCurve rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::EcCurve lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::EcCurve e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::EcCurve e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::KeyOrigin o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::KeyOrigin o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::KeyOrigin lhs, const ::android::hardware::keymaster::V3_0::KeyOrigin rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::KeyOrigin rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::KeyOrigin lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::KeyOrigin lhs, const ::android::hardware::keymaster::V3_0::KeyOrigin rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::KeyOrigin rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::KeyOrigin lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::KeyOrigin e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::KeyOrigin e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements lhs, const ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements lhs, const ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::KeyPurpose o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::KeyPurpose o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::KeyPurpose lhs, const ::android::hardware::keymaster::V3_0::KeyPurpose rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::KeyPurpose rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::KeyPurpose lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::KeyPurpose lhs, const ::android::hardware::keymaster::V3_0::KeyPurpose rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::KeyPurpose rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::KeyPurpose lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::KeyPurpose e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::KeyPurpose e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::ErrorCode o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::ErrorCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::ErrorCode lhs, const ::android::hardware::keymaster::V3_0::ErrorCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::ErrorCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::ErrorCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::ErrorCode lhs, const ::android::hardware::keymaster::V3_0::ErrorCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::ErrorCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::ErrorCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::ErrorCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::ErrorCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::KeyDerivationFunction o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::KeyDerivationFunction o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::KeyDerivationFunction lhs, const ::android::hardware::keymaster::V3_0::KeyDerivationFunction rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::KeyDerivationFunction rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::KeyDerivationFunction lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::KeyDerivationFunction lhs, const ::android::hardware::keymaster::V3_0::KeyDerivationFunction rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::KeyDerivationFunction rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::KeyDerivationFunction lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::KeyDerivationFunction e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::KeyDerivationFunction e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::HardwareAuthenticatorType o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::HardwareAuthenticatorType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType lhs, const ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType lhs, const ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams& o);
static inline void PrintTo(const ::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams& o, ::std::ostream*);
// operator== and operator!= are not generated for IntegerParams

static inline std::string toString(const ::android::hardware::keymaster::V3_0::KeyParameter& o);
static inline void PrintTo(const ::android::hardware::keymaster::V3_0::KeyParameter& o, ::std::ostream*);
// operator== and operator!= are not generated for KeyParameter

static inline std::string toString(const ::android::hardware::keymaster::V3_0::KeyCharacteristics& o);
static inline void PrintTo(const ::android::hardware::keymaster::V3_0::KeyCharacteristics& o, ::std::ostream*);
// operator== and operator!= are not generated for KeyCharacteristics

static inline std::string toString(const ::android::hardware::keymaster::V3_0::HardwareAuthToken& o);
static inline void PrintTo(const ::android::hardware::keymaster::V3_0::HardwareAuthToken& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::keymaster::V3_0::HardwareAuthToken& lhs, const ::android::hardware::keymaster::V3_0::HardwareAuthToken& rhs);
static inline bool operator!=(const ::android::hardware::keymaster::V3_0::HardwareAuthToken& lhs, const ::android::hardware::keymaster::V3_0::HardwareAuthToken& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::SecurityLevel o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::SecurityLevel o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::SecurityLevel lhs, const ::android::hardware::keymaster::V3_0::SecurityLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::SecurityLevel rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::SecurityLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::SecurityLevel lhs, const ::android::hardware::keymaster::V3_0::SecurityLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::SecurityLevel rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::SecurityLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::SecurityLevel e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::SecurityLevel e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::keymaster::V3_0::KeyFormat o);
static inline void PrintTo(::android::hardware::keymaster::V3_0::KeyFormat o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::KeyFormat lhs, const ::android::hardware::keymaster::V3_0::KeyFormat rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::KeyFormat rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::keymaster::V3_0::KeyFormat lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::KeyFormat lhs, const ::android::hardware::keymaster::V3_0::KeyFormat rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::keymaster::V3_0::KeyFormat rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::keymaster::V3_0::KeyFormat lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::keymaster::V3_0::KeyFormat e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::keymaster::V3_0::KeyFormat e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::TagType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::TagType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::TagType::INVALID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::INVALID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::TagType::ENUM) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::ENUM)) {
        os += (first ? "" : " | ");
        os += "ENUM";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::ENUM;
    }
    if ((o & ::android::hardware::keymaster::V3_0::TagType::ENUM_REP) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::ENUM_REP)) {
        os += (first ? "" : " | ");
        os += "ENUM_REP";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::ENUM_REP;
    }
    if ((o & ::android::hardware::keymaster::V3_0::TagType::UINT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::UINT)) {
        os += (first ? "" : " | ");
        os += "UINT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::UINT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::TagType::UINT_REP) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::UINT_REP)) {
        os += (first ? "" : " | ");
        os += "UINT_REP";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::UINT_REP;
    }
    if ((o & ::android::hardware::keymaster::V3_0::TagType::ULONG) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::ULONG)) {
        os += (first ? "" : " | ");
        os += "ULONG";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::ULONG;
    }
    if ((o & ::android::hardware::keymaster::V3_0::TagType::DATE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::DATE)) {
        os += (first ? "" : " | ");
        os += "DATE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::DATE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::TagType::BOOL) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::BOOL)) {
        os += (first ? "" : " | ");
        os += "BOOL";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::BOOL;
    }
    if ((o & ::android::hardware::keymaster::V3_0::TagType::BIGNUM) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::BIGNUM)) {
        os += (first ? "" : " | ");
        os += "BIGNUM";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::BIGNUM;
    }
    if ((o & ::android::hardware::keymaster::V3_0::TagType::BYTES) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::BYTES)) {
        os += (first ? "" : " | ");
        os += "BYTES";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::BYTES;
    }
    if ((o & ::android::hardware::keymaster::V3_0::TagType::ULONG_REP) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::TagType::ULONG_REP)) {
        os += (first ? "" : " | ");
        os += "ULONG_REP";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::TagType::ULONG_REP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::TagType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::TagType::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::keymaster::V3_0::TagType::ENUM) {
        return "ENUM";
    }
    if (o == ::android::hardware::keymaster::V3_0::TagType::ENUM_REP) {
        return "ENUM_REP";
    }
    if (o == ::android::hardware::keymaster::V3_0::TagType::UINT) {
        return "UINT";
    }
    if (o == ::android::hardware::keymaster::V3_0::TagType::UINT_REP) {
        return "UINT_REP";
    }
    if (o == ::android::hardware::keymaster::V3_0::TagType::ULONG) {
        return "ULONG";
    }
    if (o == ::android::hardware::keymaster::V3_0::TagType::DATE) {
        return "DATE";
    }
    if (o == ::android::hardware::keymaster::V3_0::TagType::BOOL) {
        return "BOOL";
    }
    if (o == ::android::hardware::keymaster::V3_0::TagType::BIGNUM) {
        return "BIGNUM";
    }
    if (o == ::android::hardware::keymaster::V3_0::TagType::BYTES) {
        return "BYTES";
    }
    if (o == ::android::hardware::keymaster::V3_0::TagType::ULONG_REP) {
        return "ULONG_REP";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::TagType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::Tag>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::Tag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::Tag::INVALID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::INVALID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::PURPOSE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::PURPOSE)) {
        os += (first ? "" : " | ");
        os += "PURPOSE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::PURPOSE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ALGORITHM) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ALGORITHM)) {
        os += (first ? "" : " | ");
        os += "ALGORITHM";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ALGORITHM;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::KEY_SIZE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::KEY_SIZE)) {
        os += (first ? "" : " | ");
        os += "KEY_SIZE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::KEY_SIZE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::BLOCK_MODE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::BLOCK_MODE)) {
        os += (first ? "" : " | ");
        os += "BLOCK_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::BLOCK_MODE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::DIGEST) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::DIGEST)) {
        os += (first ? "" : " | ");
        os += "DIGEST";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::DIGEST;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::PADDING) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::PADDING)) {
        os += (first ? "" : " | ");
        os += "PADDING";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::PADDING;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::CALLER_NONCE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::CALLER_NONCE)) {
        os += (first ? "" : " | ");
        os += "CALLER_NONCE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::CALLER_NONCE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::MIN_MAC_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::MIN_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "MIN_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::MIN_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::KDF) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::KDF)) {
        os += (first ? "" : " | ");
        os += "KDF";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::KDF;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::EC_CURVE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::EC_CURVE)) {
        os += (first ? "" : " | ");
        os += "EC_CURVE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::EC_CURVE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::RSA_PUBLIC_EXPONENT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::RSA_PUBLIC_EXPONENT)) {
        os += (first ? "" : " | ");
        os += "RSA_PUBLIC_EXPONENT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::RSA_PUBLIC_EXPONENT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ECIES_SINGLE_HASH_MODE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ECIES_SINGLE_HASH_MODE)) {
        os += (first ? "" : " | ");
        os += "ECIES_SINGLE_HASH_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ECIES_SINGLE_HASH_MODE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::INCLUDE_UNIQUE_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::INCLUDE_UNIQUE_ID)) {
        os += (first ? "" : " | ");
        os += "INCLUDE_UNIQUE_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::INCLUDE_UNIQUE_ID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::BLOB_USAGE_REQUIREMENTS) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::BLOB_USAGE_REQUIREMENTS)) {
        os += (first ? "" : " | ");
        os += "BLOB_USAGE_REQUIREMENTS";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::BLOB_USAGE_REQUIREMENTS;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::BOOTLOADER_ONLY) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::BOOTLOADER_ONLY)) {
        os += (first ? "" : " | ");
        os += "BOOTLOADER_ONLY";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::BOOTLOADER_ONLY;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ACTIVE_DATETIME) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ACTIVE_DATETIME)) {
        os += (first ? "" : " | ");
        os += "ACTIVE_DATETIME";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ACTIVE_DATETIME;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ORIGINATION_EXPIRE_DATETIME) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ORIGINATION_EXPIRE_DATETIME)) {
        os += (first ? "" : " | ");
        os += "ORIGINATION_EXPIRE_DATETIME";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ORIGINATION_EXPIRE_DATETIME;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::USAGE_EXPIRE_DATETIME) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::USAGE_EXPIRE_DATETIME)) {
        os += (first ? "" : " | ");
        os += "USAGE_EXPIRE_DATETIME";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::USAGE_EXPIRE_DATETIME;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::MIN_SECONDS_BETWEEN_OPS) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::MIN_SECONDS_BETWEEN_OPS)) {
        os += (first ? "" : " | ");
        os += "MIN_SECONDS_BETWEEN_OPS";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::MIN_SECONDS_BETWEEN_OPS;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::MAX_USES_PER_BOOT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::MAX_USES_PER_BOOT)) {
        os += (first ? "" : " | ");
        os += "MAX_USES_PER_BOOT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::MAX_USES_PER_BOOT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ALL_USERS) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ALL_USERS)) {
        os += (first ? "" : " | ");
        os += "ALL_USERS";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ALL_USERS;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::USER_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::USER_ID)) {
        os += (first ? "" : " | ");
        os += "USER_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::USER_ID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::USER_SECURE_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::USER_SECURE_ID)) {
        os += (first ? "" : " | ");
        os += "USER_SECURE_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::USER_SECURE_ID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::NO_AUTH_REQUIRED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::NO_AUTH_REQUIRED)) {
        os += (first ? "" : " | ");
        os += "NO_AUTH_REQUIRED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::NO_AUTH_REQUIRED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::USER_AUTH_TYPE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::USER_AUTH_TYPE)) {
        os += (first ? "" : " | ");
        os += "USER_AUTH_TYPE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::USER_AUTH_TYPE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::AUTH_TIMEOUT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::AUTH_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "AUTH_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::AUTH_TIMEOUT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ALLOW_WHILE_ON_BODY) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ALLOW_WHILE_ON_BODY)) {
        os += (first ? "" : " | ");
        os += "ALLOW_WHILE_ON_BODY";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ALLOW_WHILE_ON_BODY;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ALL_APPLICATIONS) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ALL_APPLICATIONS)) {
        os += (first ? "" : " | ");
        os += "ALL_APPLICATIONS";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ALL_APPLICATIONS;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::APPLICATION_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::APPLICATION_ID)) {
        os += (first ? "" : " | ");
        os += "APPLICATION_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::APPLICATION_ID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::EXPORTABLE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::EXPORTABLE)) {
        os += (first ? "" : " | ");
        os += "EXPORTABLE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::EXPORTABLE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::APPLICATION_DATA) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::APPLICATION_DATA)) {
        os += (first ? "" : " | ");
        os += "APPLICATION_DATA";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::APPLICATION_DATA;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::CREATION_DATETIME) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::CREATION_DATETIME)) {
        os += (first ? "" : " | ");
        os += "CREATION_DATETIME";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::CREATION_DATETIME;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ORIGIN) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ORIGIN)) {
        os += (first ? "" : " | ");
        os += "ORIGIN";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ORIGIN;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ROLLBACK_RESISTANT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ROLLBACK_RESISTANT)) {
        os += (first ? "" : " | ");
        os += "ROLLBACK_RESISTANT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ROLLBACK_RESISTANT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ROOT_OF_TRUST) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ROOT_OF_TRUST)) {
        os += (first ? "" : " | ");
        os += "ROOT_OF_TRUST";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ROOT_OF_TRUST;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::OS_VERSION) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::OS_VERSION)) {
        os += (first ? "" : " | ");
        os += "OS_VERSION";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::OS_VERSION;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::OS_PATCHLEVEL) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::OS_PATCHLEVEL)) {
        os += (first ? "" : " | ");
        os += "OS_PATCHLEVEL";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::OS_PATCHLEVEL;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::UNIQUE_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::UNIQUE_ID)) {
        os += (first ? "" : " | ");
        os += "UNIQUE_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::UNIQUE_ID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_CHALLENGE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ATTESTATION_CHALLENGE)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_CHALLENGE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_CHALLENGE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_APPLICATION_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ATTESTATION_APPLICATION_ID)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_APPLICATION_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_APPLICATION_ID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_BRAND) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_BRAND)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_BRAND";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_BRAND;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_DEVICE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_DEVICE)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_DEVICE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_DEVICE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_PRODUCT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_PRODUCT)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_PRODUCT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_PRODUCT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_SERIAL) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_SERIAL)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_SERIAL";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_SERIAL;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_IMEI) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_IMEI)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_IMEI";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_IMEI;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MEID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MEID)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_MEID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MEID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MANUFACTURER) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MANUFACTURER)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_MANUFACTURER";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MANUFACTURER;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MODEL) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MODEL)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_ID_MODEL";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MODEL;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::ASSOCIATED_DATA) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::ASSOCIATED_DATA)) {
        os += (first ? "" : " | ");
        os += "ASSOCIATED_DATA";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::ASSOCIATED_DATA;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::NONCE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::NONCE)) {
        os += (first ? "" : " | ");
        os += "NONCE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::NONCE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::AUTH_TOKEN) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::AUTH_TOKEN)) {
        os += (first ? "" : " | ");
        os += "AUTH_TOKEN";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::AUTH_TOKEN;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::MAC_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Tag::RESET_SINCE_ID_ROTATION) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Tag::RESET_SINCE_ID_ROTATION)) {
        os += (first ? "" : " | ");
        os += "RESET_SINCE_ID_ROTATION";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Tag::RESET_SINCE_ID_ROTATION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::Tag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::Tag::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::PURPOSE) {
        return "PURPOSE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ALGORITHM) {
        return "ALGORITHM";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::KEY_SIZE) {
        return "KEY_SIZE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::BLOCK_MODE) {
        return "BLOCK_MODE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::DIGEST) {
        return "DIGEST";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::PADDING) {
        return "PADDING";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::CALLER_NONCE) {
        return "CALLER_NONCE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::MIN_MAC_LENGTH) {
        return "MIN_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::KDF) {
        return "KDF";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::EC_CURVE) {
        return "EC_CURVE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::RSA_PUBLIC_EXPONENT) {
        return "RSA_PUBLIC_EXPONENT";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ECIES_SINGLE_HASH_MODE) {
        return "ECIES_SINGLE_HASH_MODE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::INCLUDE_UNIQUE_ID) {
        return "INCLUDE_UNIQUE_ID";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::BLOB_USAGE_REQUIREMENTS) {
        return "BLOB_USAGE_REQUIREMENTS";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::BOOTLOADER_ONLY) {
        return "BOOTLOADER_ONLY";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ACTIVE_DATETIME) {
        return "ACTIVE_DATETIME";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ORIGINATION_EXPIRE_DATETIME) {
        return "ORIGINATION_EXPIRE_DATETIME";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::USAGE_EXPIRE_DATETIME) {
        return "USAGE_EXPIRE_DATETIME";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::MIN_SECONDS_BETWEEN_OPS) {
        return "MIN_SECONDS_BETWEEN_OPS";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::MAX_USES_PER_BOOT) {
        return "MAX_USES_PER_BOOT";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ALL_USERS) {
        return "ALL_USERS";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::USER_ID) {
        return "USER_ID";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::USER_SECURE_ID) {
        return "USER_SECURE_ID";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::NO_AUTH_REQUIRED) {
        return "NO_AUTH_REQUIRED";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::USER_AUTH_TYPE) {
        return "USER_AUTH_TYPE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::AUTH_TIMEOUT) {
        return "AUTH_TIMEOUT";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ALLOW_WHILE_ON_BODY) {
        return "ALLOW_WHILE_ON_BODY";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ALL_APPLICATIONS) {
        return "ALL_APPLICATIONS";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::APPLICATION_ID) {
        return "APPLICATION_ID";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::EXPORTABLE) {
        return "EXPORTABLE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::APPLICATION_DATA) {
        return "APPLICATION_DATA";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::CREATION_DATETIME) {
        return "CREATION_DATETIME";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ORIGIN) {
        return "ORIGIN";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ROLLBACK_RESISTANT) {
        return "ROLLBACK_RESISTANT";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ROOT_OF_TRUST) {
        return "ROOT_OF_TRUST";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::OS_VERSION) {
        return "OS_VERSION";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::OS_PATCHLEVEL) {
        return "OS_PATCHLEVEL";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::UNIQUE_ID) {
        return "UNIQUE_ID";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_CHALLENGE) {
        return "ATTESTATION_CHALLENGE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_APPLICATION_ID) {
        return "ATTESTATION_APPLICATION_ID";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_BRAND) {
        return "ATTESTATION_ID_BRAND";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_DEVICE) {
        return "ATTESTATION_ID_DEVICE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_PRODUCT) {
        return "ATTESTATION_ID_PRODUCT";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_SERIAL) {
        return "ATTESTATION_ID_SERIAL";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_IMEI) {
        return "ATTESTATION_ID_IMEI";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MEID) {
        return "ATTESTATION_ID_MEID";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MANUFACTURER) {
        return "ATTESTATION_ID_MANUFACTURER";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MODEL) {
        return "ATTESTATION_ID_MODEL";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::ASSOCIATED_DATA) {
        return "ASSOCIATED_DATA";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::NONCE) {
        return "NONCE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::AUTH_TOKEN) {
        return "AUTH_TOKEN";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::MAC_LENGTH) {
        return "MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V3_0::Tag::RESET_SINCE_ID_ROTATION) {
        return "RESET_SINCE_ID_ROTATION";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::Tag o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::Algorithm>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::Algorithm> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::Algorithm::RSA) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Algorithm::RSA)) {
        os += (first ? "" : " | ");
        os += "RSA";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Algorithm::RSA;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Algorithm::EC) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Algorithm::EC)) {
        os += (first ? "" : " | ");
        os += "EC";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Algorithm::EC;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Algorithm::AES) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Algorithm::AES)) {
        os += (first ? "" : " | ");
        os += "AES";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Algorithm::AES;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Algorithm::HMAC) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Algorithm::HMAC)) {
        os += (first ? "" : " | ");
        os += "HMAC";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Algorithm::HMAC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::Algorithm o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::Algorithm::RSA) {
        return "RSA";
    }
    if (o == ::android::hardware::keymaster::V3_0::Algorithm::EC) {
        return "EC";
    }
    if (o == ::android::hardware::keymaster::V3_0::Algorithm::AES) {
        return "AES";
    }
    if (o == ::android::hardware::keymaster::V3_0::Algorithm::HMAC) {
        return "HMAC";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::Algorithm o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::BlockMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::BlockMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::BlockMode::ECB) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::BlockMode::ECB)) {
        os += (first ? "" : " | ");
        os += "ECB";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::BlockMode::ECB;
    }
    if ((o & ::android::hardware::keymaster::V3_0::BlockMode::CBC) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::BlockMode::CBC)) {
        os += (first ? "" : " | ");
        os += "CBC";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::BlockMode::CBC;
    }
    if ((o & ::android::hardware::keymaster::V3_0::BlockMode::CTR) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::BlockMode::CTR)) {
        os += (first ? "" : " | ");
        os += "CTR";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::BlockMode::CTR;
    }
    if ((o & ::android::hardware::keymaster::V3_0::BlockMode::GCM) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::BlockMode::GCM)) {
        os += (first ? "" : " | ");
        os += "GCM";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::BlockMode::GCM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::BlockMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::BlockMode::ECB) {
        return "ECB";
    }
    if (o == ::android::hardware::keymaster::V3_0::BlockMode::CBC) {
        return "CBC";
    }
    if (o == ::android::hardware::keymaster::V3_0::BlockMode::CTR) {
        return "CTR";
    }
    if (o == ::android::hardware::keymaster::V3_0::BlockMode::GCM) {
        return "GCM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::BlockMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::PaddingMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::PaddingMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::PaddingMode::NONE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::PaddingMode::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::PaddingMode::NONE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::PaddingMode::RSA_OAEP) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::PaddingMode::RSA_OAEP)) {
        os += (first ? "" : " | ");
        os += "RSA_OAEP";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::PaddingMode::RSA_OAEP;
    }
    if ((o & ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PSS) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::PaddingMode::RSA_PSS)) {
        os += (first ? "" : " | ");
        os += "RSA_PSS";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PSS;
    }
    if ((o & ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PKCS1_1_5_ENCRYPT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::PaddingMode::RSA_PKCS1_1_5_ENCRYPT)) {
        os += (first ? "" : " | ");
        os += "RSA_PKCS1_1_5_ENCRYPT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PKCS1_1_5_ENCRYPT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PKCS1_1_5_SIGN) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::PaddingMode::RSA_PKCS1_1_5_SIGN)) {
        os += (first ? "" : " | ");
        os += "RSA_PKCS1_1_5_SIGN";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PKCS1_1_5_SIGN;
    }
    if ((o & ::android::hardware::keymaster::V3_0::PaddingMode::PKCS7) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::PaddingMode::PKCS7)) {
        os += (first ? "" : " | ");
        os += "PKCS7";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::PaddingMode::PKCS7;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::PaddingMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::PaddingMode::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::keymaster::V3_0::PaddingMode::RSA_OAEP) {
        return "RSA_OAEP";
    }
    if (o == ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PSS) {
        return "RSA_PSS";
    }
    if (o == ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PKCS1_1_5_ENCRYPT) {
        return "RSA_PKCS1_1_5_ENCRYPT";
    }
    if (o == ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PKCS1_1_5_SIGN) {
        return "RSA_PKCS1_1_5_SIGN";
    }
    if (o == ::android::hardware::keymaster::V3_0::PaddingMode::PKCS7) {
        return "PKCS7";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::PaddingMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::Digest>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::Digest> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::Digest::NONE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Digest::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Digest::NONE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Digest::MD5) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Digest::MD5)) {
        os += (first ? "" : " | ");
        os += "MD5";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Digest::MD5;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Digest::SHA1) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Digest::SHA1)) {
        os += (first ? "" : " | ");
        os += "SHA1";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Digest::SHA1;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Digest::SHA_2_224) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Digest::SHA_2_224)) {
        os += (first ? "" : " | ");
        os += "SHA_2_224";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Digest::SHA_2_224;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Digest::SHA_2_256) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Digest::SHA_2_256)) {
        os += (first ? "" : " | ");
        os += "SHA_2_256";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Digest::SHA_2_256;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Digest::SHA_2_384) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Digest::SHA_2_384)) {
        os += (first ? "" : " | ");
        os += "SHA_2_384";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Digest::SHA_2_384;
    }
    if ((o & ::android::hardware::keymaster::V3_0::Digest::SHA_2_512) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::Digest::SHA_2_512)) {
        os += (first ? "" : " | ");
        os += "SHA_2_512";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::Digest::SHA_2_512;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::Digest o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::Digest::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::keymaster::V3_0::Digest::MD5) {
        return "MD5";
    }
    if (o == ::android::hardware::keymaster::V3_0::Digest::SHA1) {
        return "SHA1";
    }
    if (o == ::android::hardware::keymaster::V3_0::Digest::SHA_2_224) {
        return "SHA_2_224";
    }
    if (o == ::android::hardware::keymaster::V3_0::Digest::SHA_2_256) {
        return "SHA_2_256";
    }
    if (o == ::android::hardware::keymaster::V3_0::Digest::SHA_2_384) {
        return "SHA_2_384";
    }
    if (o == ::android::hardware::keymaster::V3_0::Digest::SHA_2_512) {
        return "SHA_2_512";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::Digest o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::EcCurve>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::EcCurve> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::EcCurve::P_224) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::EcCurve::P_224)) {
        os += (first ? "" : " | ");
        os += "P_224";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::EcCurve::P_224;
    }
    if ((o & ::android::hardware::keymaster::V3_0::EcCurve::P_256) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::EcCurve::P_256)) {
        os += (first ? "" : " | ");
        os += "P_256";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::EcCurve::P_256;
    }
    if ((o & ::android::hardware::keymaster::V3_0::EcCurve::P_384) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::EcCurve::P_384)) {
        os += (first ? "" : " | ");
        os += "P_384";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::EcCurve::P_384;
    }
    if ((o & ::android::hardware::keymaster::V3_0::EcCurve::P_521) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::EcCurve::P_521)) {
        os += (first ? "" : " | ");
        os += "P_521";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::EcCurve::P_521;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::EcCurve o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::EcCurve::P_224) {
        return "P_224";
    }
    if (o == ::android::hardware::keymaster::V3_0::EcCurve::P_256) {
        return "P_256";
    }
    if (o == ::android::hardware::keymaster::V3_0::EcCurve::P_384) {
        return "P_384";
    }
    if (o == ::android::hardware::keymaster::V3_0::EcCurve::P_521) {
        return "P_521";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::EcCurve o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::KeyOrigin>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::KeyOrigin> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::KeyOrigin::GENERATED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyOrigin::GENERATED)) {
        os += (first ? "" : " | ");
        os += "GENERATED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyOrigin::GENERATED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyOrigin::DERIVED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyOrigin::DERIVED)) {
        os += (first ? "" : " | ");
        os += "DERIVED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyOrigin::DERIVED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyOrigin::IMPORTED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyOrigin::IMPORTED)) {
        os += (first ? "" : " | ");
        os += "IMPORTED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyOrigin::IMPORTED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyOrigin::UNKNOWN) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyOrigin::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyOrigin::UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::KeyOrigin o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::KeyOrigin::GENERATED) {
        return "GENERATED";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyOrigin::DERIVED) {
        return "DERIVED";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyOrigin::IMPORTED) {
        return "IMPORTED";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyOrigin::UNKNOWN) {
        return "UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::KeyOrigin o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements::STANDALONE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements::STANDALONE)) {
        os += (first ? "" : " | ");
        os += "STANDALONE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements::STANDALONE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements::REQUIRES_FILE_SYSTEM) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements::REQUIRES_FILE_SYSTEM)) {
        os += (first ? "" : " | ");
        os += "REQUIRES_FILE_SYSTEM";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements::REQUIRES_FILE_SYSTEM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements::STANDALONE) {
        return "STANDALONE";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements::REQUIRES_FILE_SYSTEM) {
        return "REQUIRES_FILE_SYSTEM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::KeyPurpose>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::KeyPurpose> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::KeyPurpose::ENCRYPT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyPurpose::ENCRYPT)) {
        os += (first ? "" : " | ");
        os += "ENCRYPT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyPurpose::ENCRYPT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyPurpose::DECRYPT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyPurpose::DECRYPT)) {
        os += (first ? "" : " | ");
        os += "DECRYPT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyPurpose::DECRYPT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyPurpose::SIGN) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyPurpose::SIGN)) {
        os += (first ? "" : " | ");
        os += "SIGN";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyPurpose::SIGN;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyPurpose::VERIFY) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyPurpose::VERIFY)) {
        os += (first ? "" : " | ");
        os += "VERIFY";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyPurpose::VERIFY;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyPurpose::DERIVE_KEY) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyPurpose::DERIVE_KEY)) {
        os += (first ? "" : " | ");
        os += "DERIVE_KEY";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyPurpose::DERIVE_KEY;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyPurpose::WRAP_KEY) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyPurpose::WRAP_KEY)) {
        os += (first ? "" : " | ");
        os += "WRAP_KEY";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyPurpose::WRAP_KEY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::KeyPurpose o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::KeyPurpose::ENCRYPT) {
        return "ENCRYPT";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyPurpose::DECRYPT) {
        return "DECRYPT";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyPurpose::SIGN) {
        return "SIGN";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyPurpose::VERIFY) {
        return "VERIFY";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyPurpose::DERIVE_KEY) {
        return "DERIVE_KEY";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyPurpose::WRAP_KEY) {
        return "WRAP_KEY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::KeyPurpose o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::ErrorCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::ErrorCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::OK) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::OK;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::ROOT_OF_TRUST_ALREADY_SET) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::ROOT_OF_TRUST_ALREADY_SET)) {
        os += (first ? "" : " | ");
        os += "ROOT_OF_TRUST_ALREADY_SET";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::ROOT_OF_TRUST_ALREADY_SET;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_PURPOSE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_PURPOSE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_PURPOSE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_PURPOSE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_PURPOSE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_PURPOSE)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_PURPOSE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_PURPOSE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_ALGORITHM) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_ALGORITHM)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_ALGORITHM";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_ALGORITHM;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_ALGORITHM) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_ALGORITHM)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_ALGORITHM";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_ALGORITHM;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_SIZE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_SIZE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_KEY_SIZE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_SIZE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_BLOCK_MODE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_BLOCK_MODE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_BLOCK_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_BLOCK_MODE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_BLOCK_MODE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_BLOCK_MODE)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_BLOCK_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_BLOCK_MODE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_MAC_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_PADDING_MODE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_PADDING_MODE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_PADDING_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_PADDING_MODE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_PADDING_MODE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_PADDING_MODE)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_PADDING_MODE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_PADDING_MODE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_DIGEST) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_DIGEST)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_DIGEST";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_DIGEST;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_DIGEST) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_DIGEST)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_DIGEST";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_DIGEST;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_EXPIRATION_TIME) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INVALID_EXPIRATION_TIME)) {
        os += (first ? "" : " | ");
        os += "INVALID_EXPIRATION_TIME";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_EXPIRATION_TIME;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_USER_ID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INVALID_USER_ID)) {
        os += (first ? "" : " | ");
        os += "INVALID_USER_ID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_USER_ID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_AUTHORIZATION_TIMEOUT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INVALID_AUTHORIZATION_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "INVALID_AUTHORIZATION_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_AUTHORIZATION_TIMEOUT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_FORMAT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_FORMAT)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_KEY_FORMAT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_FORMAT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_KEY_FORMAT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_KEY_FORMAT)) {
        os += (first ? "" : " | ");
        os += "INCOMPATIBLE_KEY_FORMAT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_KEY_FORMAT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_VERIFICATION_ALGORITHM) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_VERIFICATION_ALGORITHM)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_KEY_VERIFICATION_ALGORITHM";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_VERIFICATION_ALGORITHM;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_INPUT_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INVALID_INPUT_LENGTH)) {
        os += (first ? "" : " | ");
        os += "INVALID_INPUT_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_INPUT_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::KEY_EXPORT_OPTIONS_INVALID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::KEY_EXPORT_OPTIONS_INVALID)) {
        os += (first ? "" : " | ");
        os += "KEY_EXPORT_OPTIONS_INVALID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::KEY_EXPORT_OPTIONS_INVALID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::DELEGATION_NOT_ALLOWED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::DELEGATION_NOT_ALLOWED)) {
        os += (first ? "" : " | ");
        os += "DELEGATION_NOT_ALLOWED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::DELEGATION_NOT_ALLOWED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::KEY_NOT_YET_VALID) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::KEY_NOT_YET_VALID)) {
        os += (first ? "" : " | ");
        os += "KEY_NOT_YET_VALID";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::KEY_NOT_YET_VALID;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::KEY_EXPIRED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::KEY_EXPIRED)) {
        os += (first ? "" : " | ");
        os += "KEY_EXPIRED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::KEY_EXPIRED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::KEY_USER_NOT_AUTHENTICATED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::KEY_USER_NOT_AUTHENTICATED)) {
        os += (first ? "" : " | ");
        os += "KEY_USER_NOT_AUTHENTICATED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::KEY_USER_NOT_AUTHENTICATED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::OUTPUT_PARAMETER_NULL) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::OUTPUT_PARAMETER_NULL)) {
        os += (first ? "" : " | ");
        os += "OUTPUT_PARAMETER_NULL";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::OUTPUT_PARAMETER_NULL;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_OPERATION_HANDLE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INVALID_OPERATION_HANDLE)) {
        os += (first ? "" : " | ");
        os += "INVALID_OPERATION_HANDLE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_OPERATION_HANDLE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INSUFFICIENT_BUFFER_SPACE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INSUFFICIENT_BUFFER_SPACE)) {
        os += (first ? "" : " | ");
        os += "INSUFFICIENT_BUFFER_SPACE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INSUFFICIENT_BUFFER_SPACE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::VERIFICATION_FAILED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::VERIFICATION_FAILED)) {
        os += (first ? "" : " | ");
        os += "VERIFICATION_FAILED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::VERIFICATION_FAILED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::TOO_MANY_OPERATIONS) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::TOO_MANY_OPERATIONS)) {
        os += (first ? "" : " | ");
        os += "TOO_MANY_OPERATIONS";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::TOO_MANY_OPERATIONS;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNEXPECTED_NULL_POINTER) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNEXPECTED_NULL_POINTER)) {
        os += (first ? "" : " | ");
        os += "UNEXPECTED_NULL_POINTER";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNEXPECTED_NULL_POINTER;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_KEY_BLOB) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INVALID_KEY_BLOB)) {
        os += (first ? "" : " | ");
        os += "INVALID_KEY_BLOB";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_KEY_BLOB;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_NOT_ENCRYPTED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_NOT_ENCRYPTED)) {
        os += (first ? "" : " | ");
        os += "IMPORTED_KEY_NOT_ENCRYPTED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_NOT_ENCRYPTED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_DECRYPTION_FAILED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_DECRYPTION_FAILED)) {
        os += (first ? "" : " | ");
        os += "IMPORTED_KEY_DECRYPTION_FAILED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_DECRYPTION_FAILED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_NOT_SIGNED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_NOT_SIGNED)) {
        os += (first ? "" : " | ");
        os += "IMPORTED_KEY_NOT_SIGNED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_NOT_SIGNED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_VERIFICATION_FAILED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_VERIFICATION_FAILED)) {
        os += (first ? "" : " | ");
        os += "IMPORTED_KEY_VERIFICATION_FAILED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_VERIFICATION_FAILED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_ARGUMENT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INVALID_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_ARGUMENT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_TAG) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_TAG)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_TAG";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_TAG;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_TAG) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INVALID_TAG)) {
        os += (first ? "" : " | ");
        os += "INVALID_TAG";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_TAG;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::MEMORY_ALLOCATION_FAILED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::MEMORY_ALLOCATION_FAILED)) {
        os += (first ? "" : " | ");
        os += "MEMORY_ALLOCATION_FAILED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::MEMORY_ALLOCATION_FAILED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::IMPORT_PARAMETER_MISMATCH) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::IMPORT_PARAMETER_MISMATCH)) {
        os += (first ? "" : " | ");
        os += "IMPORT_PARAMETER_MISMATCH";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::IMPORT_PARAMETER_MISMATCH;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_ACCESS_DENIED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_ACCESS_DENIED)) {
        os += (first ? "" : " | ");
        os += "SECURE_HW_ACCESS_DENIED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_ACCESS_DENIED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::OPERATION_CANCELLED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::OPERATION_CANCELLED)) {
        os += (first ? "" : " | ");
        os += "OPERATION_CANCELLED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::OPERATION_CANCELLED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::CONCURRENT_ACCESS_CONFLICT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::CONCURRENT_ACCESS_CONFLICT)) {
        os += (first ? "" : " | ");
        os += "CONCURRENT_ACCESS_CONFLICT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::CONCURRENT_ACCESS_CONFLICT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_BUSY) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_BUSY)) {
        os += (first ? "" : " | ");
        os += "SECURE_HW_BUSY";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_BUSY;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_COMMUNICATION_FAILED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_COMMUNICATION_FAILED)) {
        os += (first ? "" : " | ");
        os += "SECURE_HW_COMMUNICATION_FAILED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_COMMUNICATION_FAILED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_EC_FIELD) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_EC_FIELD)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_EC_FIELD";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_EC_FIELD;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_NONCE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::MISSING_NONCE)) {
        os += (first ? "" : " | ");
        os += "MISSING_NONCE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_NONCE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_NONCE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INVALID_NONCE)) {
        os += (first ? "" : " | ");
        os += "INVALID_NONCE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_NONCE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_MAC_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::MISSING_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "MISSING_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::KEY_RATE_LIMIT_EXCEEDED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::KEY_RATE_LIMIT_EXCEEDED)) {
        os += (first ? "" : " | ");
        os += "KEY_RATE_LIMIT_EXCEEDED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::KEY_RATE_LIMIT_EXCEEDED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::CALLER_NONCE_PROHIBITED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::CALLER_NONCE_PROHIBITED)) {
        os += (first ? "" : " | ");
        os += "CALLER_NONCE_PROHIBITED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::CALLER_NONCE_PROHIBITED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::KEY_MAX_OPS_EXCEEDED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::KEY_MAX_OPS_EXCEEDED)) {
        os += (first ? "" : " | ");
        os += "KEY_MAX_OPS_EXCEEDED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::KEY_MAX_OPS_EXCEEDED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_MAC_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::INVALID_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "INVALID_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_MIN_MAC_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::MISSING_MIN_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "MISSING_MIN_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_MIN_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_MIN_MAC_LENGTH";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KDF) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KDF)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_KDF";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KDF;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_EC_CURVE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_EC_CURVE)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_EC_CURVE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_EC_CURVE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::KEY_REQUIRES_UPGRADE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::KEY_REQUIRES_UPGRADE)) {
        os += (first ? "" : " | ");
        os += "KEY_REQUIRES_UPGRADE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::KEY_REQUIRES_UPGRADE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::ATTESTATION_CHALLENGE_MISSING) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::ATTESTATION_CHALLENGE_MISSING)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_CHALLENGE_MISSING";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::ATTESTATION_CHALLENGE_MISSING;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::KEYMASTER_NOT_CONFIGURED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::KEYMASTER_NOT_CONFIGURED)) {
        os += (first ? "" : " | ");
        os += "KEYMASTER_NOT_CONFIGURED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::KEYMASTER_NOT_CONFIGURED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::ATTESTATION_APPLICATION_ID_MISSING) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::ATTESTATION_APPLICATION_ID_MISSING)) {
        os += (first ? "" : " | ");
        os += "ATTESTATION_APPLICATION_ID_MISSING";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::ATTESTATION_APPLICATION_ID_MISSING;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::CANNOT_ATTEST_IDS) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::CANNOT_ATTEST_IDS)) {
        os += (first ? "" : " | ");
        os += "CANNOT_ATTEST_IDS";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::CANNOT_ATTEST_IDS;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNIMPLEMENTED) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNIMPLEMENTED)) {
        os += (first ? "" : " | ");
        os += "UNIMPLEMENTED";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNIMPLEMENTED;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::VERSION_MISMATCH) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::VERSION_MISMATCH)) {
        os += (first ? "" : " | ");
        os += "VERSION_MISMATCH";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::VERSION_MISMATCH;
    }
    if ((o & ::android::hardware::keymaster::V3_0::ErrorCode::UNKNOWN_ERROR) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::ErrorCode::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::ErrorCode::UNKNOWN_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::ErrorCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::OK) {
        return "OK";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::ROOT_OF_TRUST_ALREADY_SET) {
        return "ROOT_OF_TRUST_ALREADY_SET";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_PURPOSE) {
        return "UNSUPPORTED_PURPOSE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_PURPOSE) {
        return "INCOMPATIBLE_PURPOSE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_ALGORITHM) {
        return "UNSUPPORTED_ALGORITHM";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_ALGORITHM) {
        return "INCOMPATIBLE_ALGORITHM";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_SIZE) {
        return "UNSUPPORTED_KEY_SIZE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_BLOCK_MODE) {
        return "UNSUPPORTED_BLOCK_MODE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_BLOCK_MODE) {
        return "INCOMPATIBLE_BLOCK_MODE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_MAC_LENGTH) {
        return "UNSUPPORTED_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_PADDING_MODE) {
        return "UNSUPPORTED_PADDING_MODE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_PADDING_MODE) {
        return "INCOMPATIBLE_PADDING_MODE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_DIGEST) {
        return "UNSUPPORTED_DIGEST";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_DIGEST) {
        return "INCOMPATIBLE_DIGEST";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_EXPIRATION_TIME) {
        return "INVALID_EXPIRATION_TIME";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_USER_ID) {
        return "INVALID_USER_ID";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_AUTHORIZATION_TIMEOUT) {
        return "INVALID_AUTHORIZATION_TIMEOUT";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_FORMAT) {
        return "UNSUPPORTED_KEY_FORMAT";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_KEY_FORMAT) {
        return "INCOMPATIBLE_KEY_FORMAT";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM) {
        return "UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_VERIFICATION_ALGORITHM) {
        return "UNSUPPORTED_KEY_VERIFICATION_ALGORITHM";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_INPUT_LENGTH) {
        return "INVALID_INPUT_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::KEY_EXPORT_OPTIONS_INVALID) {
        return "KEY_EXPORT_OPTIONS_INVALID";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::DELEGATION_NOT_ALLOWED) {
        return "DELEGATION_NOT_ALLOWED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::KEY_NOT_YET_VALID) {
        return "KEY_NOT_YET_VALID";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::KEY_EXPIRED) {
        return "KEY_EXPIRED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::KEY_USER_NOT_AUTHENTICATED) {
        return "KEY_USER_NOT_AUTHENTICATED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::OUTPUT_PARAMETER_NULL) {
        return "OUTPUT_PARAMETER_NULL";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_OPERATION_HANDLE) {
        return "INVALID_OPERATION_HANDLE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INSUFFICIENT_BUFFER_SPACE) {
        return "INSUFFICIENT_BUFFER_SPACE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::VERIFICATION_FAILED) {
        return "VERIFICATION_FAILED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::TOO_MANY_OPERATIONS) {
        return "TOO_MANY_OPERATIONS";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNEXPECTED_NULL_POINTER) {
        return "UNEXPECTED_NULL_POINTER";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_KEY_BLOB) {
        return "INVALID_KEY_BLOB";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_NOT_ENCRYPTED) {
        return "IMPORTED_KEY_NOT_ENCRYPTED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_DECRYPTION_FAILED) {
        return "IMPORTED_KEY_DECRYPTION_FAILED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_NOT_SIGNED) {
        return "IMPORTED_KEY_NOT_SIGNED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_VERIFICATION_FAILED) {
        return "IMPORTED_KEY_VERIFICATION_FAILED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_ARGUMENT) {
        return "INVALID_ARGUMENT";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_TAG) {
        return "UNSUPPORTED_TAG";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_TAG) {
        return "INVALID_TAG";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::MEMORY_ALLOCATION_FAILED) {
        return "MEMORY_ALLOCATION_FAILED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::IMPORT_PARAMETER_MISMATCH) {
        return "IMPORT_PARAMETER_MISMATCH";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_ACCESS_DENIED) {
        return "SECURE_HW_ACCESS_DENIED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::OPERATION_CANCELLED) {
        return "OPERATION_CANCELLED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::CONCURRENT_ACCESS_CONFLICT) {
        return "CONCURRENT_ACCESS_CONFLICT";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_BUSY) {
        return "SECURE_HW_BUSY";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_COMMUNICATION_FAILED) {
        return "SECURE_HW_COMMUNICATION_FAILED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_EC_FIELD) {
        return "UNSUPPORTED_EC_FIELD";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_NONCE) {
        return "MISSING_NONCE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_NONCE) {
        return "INVALID_NONCE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_MAC_LENGTH) {
        return "MISSING_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::KEY_RATE_LIMIT_EXCEEDED) {
        return "KEY_RATE_LIMIT_EXCEEDED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::CALLER_NONCE_PROHIBITED) {
        return "CALLER_NONCE_PROHIBITED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::KEY_MAX_OPS_EXCEEDED) {
        return "KEY_MAX_OPS_EXCEEDED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_MAC_LENGTH) {
        return "INVALID_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_MIN_MAC_LENGTH) {
        return "MISSING_MIN_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH) {
        return "UNSUPPORTED_MIN_MAC_LENGTH";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KDF) {
        return "UNSUPPORTED_KDF";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_EC_CURVE) {
        return "UNSUPPORTED_EC_CURVE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::KEY_REQUIRES_UPGRADE) {
        return "KEY_REQUIRES_UPGRADE";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::ATTESTATION_CHALLENGE_MISSING) {
        return "ATTESTATION_CHALLENGE_MISSING";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::KEYMASTER_NOT_CONFIGURED) {
        return "KEYMASTER_NOT_CONFIGURED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::ATTESTATION_APPLICATION_ID_MISSING) {
        return "ATTESTATION_APPLICATION_ID_MISSING";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::CANNOT_ATTEST_IDS) {
        return "CANNOT_ATTEST_IDS";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNIMPLEMENTED) {
        return "UNIMPLEMENTED";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::VERSION_MISMATCH) {
        return "VERSION_MISMATCH";
    }
    if (o == ::android::hardware::keymaster::V3_0::ErrorCode::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::ErrorCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::KeyDerivationFunction>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::KeyDerivationFunction> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::KeyDerivationFunction::NONE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyDerivationFunction::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyDerivationFunction::NONE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyDerivationFunction::RFC5869_SHA256) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyDerivationFunction::RFC5869_SHA256)) {
        os += (first ? "" : " | ");
        os += "RFC5869_SHA256";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyDerivationFunction::RFC5869_SHA256;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA1) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA1)) {
        os += (first ? "" : " | ");
        os += "ISO18033_2_KDF1_SHA1";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA1;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA256) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA256)) {
        os += (first ? "" : " | ");
        os += "ISO18033_2_KDF1_SHA256";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA256;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA1) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA1)) {
        os += (first ? "" : " | ");
        os += "ISO18033_2_KDF2_SHA1";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA1;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA256) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA256)) {
        os += (first ? "" : " | ");
        os += "ISO18033_2_KDF2_SHA256";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA256;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::KeyDerivationFunction o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::KeyDerivationFunction::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyDerivationFunction::RFC5869_SHA256) {
        return "RFC5869_SHA256";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA1) {
        return "ISO18033_2_KDF1_SHA1";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA256) {
        return "ISO18033_2_KDF1_SHA256";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA1) {
        return "ISO18033_2_KDF2_SHA1";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA256) {
        return "ISO18033_2_KDF2_SHA256";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::KeyDerivationFunction o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::HardwareAuthenticatorType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::HardwareAuthenticatorType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::NONE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::NONE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::PASSWORD) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::PASSWORD)) {
        os += (first ? "" : " | ");
        os += "PASSWORD";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::PASSWORD;
    }
    if ((o & ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::FINGERPRINT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::FINGERPRINT)) {
        os += (first ? "" : " | ");
        os += "FINGERPRINT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::FINGERPRINT;
    }
    if ((o & ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::ANY) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::ANY)) {
        os += (first ? "" : " | ");
        os += "ANY";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::ANY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::HardwareAuthenticatorType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::PASSWORD) {
        return "PASSWORD";
    }
    if (o == ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::FINGERPRINT) {
        return "FINGERPRINT";
    }
    if (o == ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::ANY) {
        return "ANY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::HardwareAuthenticatorType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".algorithm = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.algorithm);
    os += ", .blockMode = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.blockMode);
    os += ", .paddingMode = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.paddingMode);
    os += ", .digest = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.digest);
    os += ", .ecCurve = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.ecCurve);
    os += ", .origin = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.origin);
    os += ", .keyBlobUsageRequirements = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.keyBlobUsageRequirements);
    os += ", .purpose = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.purpose);
    os += ", .keyDerivationFunction = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.keyDerivationFunction);
    os += ", .hardwareAuthenticatorType = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.hardwareAuthenticatorType);
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

static inline void PrintTo(const ::android::hardware::keymaster::V3_0::KeyParameter::IntegerParams& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for IntegerParams

static inline std::string toString(const ::android::hardware::keymaster::V3_0::KeyParameter& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tag = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.tag);
    os += ", .f = ";
    os += ::android::hardware::keymaster::V3_0::toString(o.f);
    os += ", .blob = ";
    os += ::android::hardware::toString(o.blob);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::keymaster::V3_0::KeyParameter& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for KeyParameter

static inline std::string toString(const ::android::hardware::keymaster::V3_0::KeyCharacteristics& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".softwareEnforced = ";
    os += ::android::hardware::toString(o.softwareEnforced);
    os += ", .teeEnforced = ";
    os += ::android::hardware::toString(o.teeEnforced);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::keymaster::V3_0::KeyCharacteristics& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for KeyCharacteristics

static inline std::string toString(const ::android::hardware::keymaster::V3_0::HardwareAuthToken& o) {
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
    os += ::android::hardware::toString(o.authenticatorType);
    os += ", .timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += ", .hmac = ";
    os += ::android::hardware::toString(o.hmac);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::keymaster::V3_0::HardwareAuthToken& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::keymaster::V3_0::HardwareAuthToken& lhs, const ::android::hardware::keymaster::V3_0::HardwareAuthToken& rhs) {
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
    if (lhs.hmac != rhs.hmac) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::keymaster::V3_0::HardwareAuthToken& lhs, const ::android::hardware::keymaster::V3_0::HardwareAuthToken& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::SecurityLevel>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::SecurityLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::SecurityLevel::SOFTWARE) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::SecurityLevel::SOFTWARE)) {
        os += (first ? "" : " | ");
        os += "SOFTWARE";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::SecurityLevel::SOFTWARE;
    }
    if ((o & ::android::hardware::keymaster::V3_0::SecurityLevel::TRUSTED_ENVIRONMENT) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::SecurityLevel::TRUSTED_ENVIRONMENT)) {
        os += (first ? "" : " | ");
        os += "TRUSTED_ENVIRONMENT";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::SecurityLevel::TRUSTED_ENVIRONMENT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::SecurityLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::SecurityLevel::SOFTWARE) {
        return "SOFTWARE";
    }
    if (o == ::android::hardware::keymaster::V3_0::SecurityLevel::TRUSTED_ENVIRONMENT) {
        return "TRUSTED_ENVIRONMENT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::SecurityLevel o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::keymaster::V3_0::KeyFormat>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::keymaster::V3_0::KeyFormat> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::keymaster::V3_0::KeyFormat::X509) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyFormat::X509)) {
        os += (first ? "" : " | ");
        os += "X509";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyFormat::X509;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyFormat::PKCS8) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyFormat::PKCS8)) {
        os += (first ? "" : " | ");
        os += "PKCS8";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyFormat::PKCS8;
    }
    if ((o & ::android::hardware::keymaster::V3_0::KeyFormat::RAW) == static_cast<uint32_t>(::android::hardware::keymaster::V3_0::KeyFormat::RAW)) {
        os += (first ? "" : " | ");
        os += "RAW";
        first = false;
        flipped |= ::android::hardware::keymaster::V3_0::KeyFormat::RAW;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::keymaster::V3_0::KeyFormat o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::keymaster::V3_0::KeyFormat::X509) {
        return "X509";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyFormat::PKCS8) {
        return "PKCS8";
    }
    if (o == ::android::hardware::keymaster::V3_0::KeyFormat::RAW) {
        return "RAW";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::keymaster::V3_0::KeyFormat o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V3_0
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::TagType, 11> hidl_enum_values<::android::hardware::keymaster::V3_0::TagType> = {
    ::android::hardware::keymaster::V3_0::TagType::INVALID,
    ::android::hardware::keymaster::V3_0::TagType::ENUM,
    ::android::hardware::keymaster::V3_0::TagType::ENUM_REP,
    ::android::hardware::keymaster::V3_0::TagType::UINT,
    ::android::hardware::keymaster::V3_0::TagType::UINT_REP,
    ::android::hardware::keymaster::V3_0::TagType::ULONG,
    ::android::hardware::keymaster::V3_0::TagType::DATE,
    ::android::hardware::keymaster::V3_0::TagType::BOOL,
    ::android::hardware::keymaster::V3_0::TagType::BIGNUM,
    ::android::hardware::keymaster::V3_0::TagType::BYTES,
    ::android::hardware::keymaster::V3_0::TagType::ULONG_REP,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::Tag, 54> hidl_enum_values<::android::hardware::keymaster::V3_0::Tag> = {
    ::android::hardware::keymaster::V3_0::Tag::INVALID,
    ::android::hardware::keymaster::V3_0::Tag::PURPOSE,
    ::android::hardware::keymaster::V3_0::Tag::ALGORITHM,
    ::android::hardware::keymaster::V3_0::Tag::KEY_SIZE,
    ::android::hardware::keymaster::V3_0::Tag::BLOCK_MODE,
    ::android::hardware::keymaster::V3_0::Tag::DIGEST,
    ::android::hardware::keymaster::V3_0::Tag::PADDING,
    ::android::hardware::keymaster::V3_0::Tag::CALLER_NONCE,
    ::android::hardware::keymaster::V3_0::Tag::MIN_MAC_LENGTH,
    ::android::hardware::keymaster::V3_0::Tag::KDF,
    ::android::hardware::keymaster::V3_0::Tag::EC_CURVE,
    ::android::hardware::keymaster::V3_0::Tag::RSA_PUBLIC_EXPONENT,
    ::android::hardware::keymaster::V3_0::Tag::ECIES_SINGLE_HASH_MODE,
    ::android::hardware::keymaster::V3_0::Tag::INCLUDE_UNIQUE_ID,
    ::android::hardware::keymaster::V3_0::Tag::BLOB_USAGE_REQUIREMENTS,
    ::android::hardware::keymaster::V3_0::Tag::BOOTLOADER_ONLY,
    ::android::hardware::keymaster::V3_0::Tag::ACTIVE_DATETIME,
    ::android::hardware::keymaster::V3_0::Tag::ORIGINATION_EXPIRE_DATETIME,
    ::android::hardware::keymaster::V3_0::Tag::USAGE_EXPIRE_DATETIME,
    ::android::hardware::keymaster::V3_0::Tag::MIN_SECONDS_BETWEEN_OPS,
    ::android::hardware::keymaster::V3_0::Tag::MAX_USES_PER_BOOT,
    ::android::hardware::keymaster::V3_0::Tag::ALL_USERS,
    ::android::hardware::keymaster::V3_0::Tag::USER_ID,
    ::android::hardware::keymaster::V3_0::Tag::USER_SECURE_ID,
    ::android::hardware::keymaster::V3_0::Tag::NO_AUTH_REQUIRED,
    ::android::hardware::keymaster::V3_0::Tag::USER_AUTH_TYPE,
    ::android::hardware::keymaster::V3_0::Tag::AUTH_TIMEOUT,
    ::android::hardware::keymaster::V3_0::Tag::ALLOW_WHILE_ON_BODY,
    ::android::hardware::keymaster::V3_0::Tag::ALL_APPLICATIONS,
    ::android::hardware::keymaster::V3_0::Tag::APPLICATION_ID,
    ::android::hardware::keymaster::V3_0::Tag::EXPORTABLE,
    ::android::hardware::keymaster::V3_0::Tag::APPLICATION_DATA,
    ::android::hardware::keymaster::V3_0::Tag::CREATION_DATETIME,
    ::android::hardware::keymaster::V3_0::Tag::ORIGIN,
    ::android::hardware::keymaster::V3_0::Tag::ROLLBACK_RESISTANT,
    ::android::hardware::keymaster::V3_0::Tag::ROOT_OF_TRUST,
    ::android::hardware::keymaster::V3_0::Tag::OS_VERSION,
    ::android::hardware::keymaster::V3_0::Tag::OS_PATCHLEVEL,
    ::android::hardware::keymaster::V3_0::Tag::UNIQUE_ID,
    ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_CHALLENGE,
    ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_APPLICATION_ID,
    ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_BRAND,
    ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_DEVICE,
    ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_PRODUCT,
    ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_SERIAL,
    ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_IMEI,
    ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MEID,
    ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MANUFACTURER,
    ::android::hardware::keymaster::V3_0::Tag::ATTESTATION_ID_MODEL,
    ::android::hardware::keymaster::V3_0::Tag::ASSOCIATED_DATA,
    ::android::hardware::keymaster::V3_0::Tag::NONCE,
    ::android::hardware::keymaster::V3_0::Tag::AUTH_TOKEN,
    ::android::hardware::keymaster::V3_0::Tag::MAC_LENGTH,
    ::android::hardware::keymaster::V3_0::Tag::RESET_SINCE_ID_ROTATION,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::Algorithm, 4> hidl_enum_values<::android::hardware::keymaster::V3_0::Algorithm> = {
    ::android::hardware::keymaster::V3_0::Algorithm::RSA,
    ::android::hardware::keymaster::V3_0::Algorithm::EC,
    ::android::hardware::keymaster::V3_0::Algorithm::AES,
    ::android::hardware::keymaster::V3_0::Algorithm::HMAC,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::BlockMode, 4> hidl_enum_values<::android::hardware::keymaster::V3_0::BlockMode> = {
    ::android::hardware::keymaster::V3_0::BlockMode::ECB,
    ::android::hardware::keymaster::V3_0::BlockMode::CBC,
    ::android::hardware::keymaster::V3_0::BlockMode::CTR,
    ::android::hardware::keymaster::V3_0::BlockMode::GCM,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::PaddingMode, 6> hidl_enum_values<::android::hardware::keymaster::V3_0::PaddingMode> = {
    ::android::hardware::keymaster::V3_0::PaddingMode::NONE,
    ::android::hardware::keymaster::V3_0::PaddingMode::RSA_OAEP,
    ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PSS,
    ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PKCS1_1_5_ENCRYPT,
    ::android::hardware::keymaster::V3_0::PaddingMode::RSA_PKCS1_1_5_SIGN,
    ::android::hardware::keymaster::V3_0::PaddingMode::PKCS7,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::Digest, 7> hidl_enum_values<::android::hardware::keymaster::V3_0::Digest> = {
    ::android::hardware::keymaster::V3_0::Digest::NONE,
    ::android::hardware::keymaster::V3_0::Digest::MD5,
    ::android::hardware::keymaster::V3_0::Digest::SHA1,
    ::android::hardware::keymaster::V3_0::Digest::SHA_2_224,
    ::android::hardware::keymaster::V3_0::Digest::SHA_2_256,
    ::android::hardware::keymaster::V3_0::Digest::SHA_2_384,
    ::android::hardware::keymaster::V3_0::Digest::SHA_2_512,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::EcCurve, 4> hidl_enum_values<::android::hardware::keymaster::V3_0::EcCurve> = {
    ::android::hardware::keymaster::V3_0::EcCurve::P_224,
    ::android::hardware::keymaster::V3_0::EcCurve::P_256,
    ::android::hardware::keymaster::V3_0::EcCurve::P_384,
    ::android::hardware::keymaster::V3_0::EcCurve::P_521,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::KeyOrigin, 4> hidl_enum_values<::android::hardware::keymaster::V3_0::KeyOrigin> = {
    ::android::hardware::keymaster::V3_0::KeyOrigin::GENERATED,
    ::android::hardware::keymaster::V3_0::KeyOrigin::DERIVED,
    ::android::hardware::keymaster::V3_0::KeyOrigin::IMPORTED,
    ::android::hardware::keymaster::V3_0::KeyOrigin::UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements, 2> hidl_enum_values<::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements> = {
    ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements::STANDALONE,
    ::android::hardware::keymaster::V3_0::KeyBlobUsageRequirements::REQUIRES_FILE_SYSTEM,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::KeyPurpose, 6> hidl_enum_values<::android::hardware::keymaster::V3_0::KeyPurpose> = {
    ::android::hardware::keymaster::V3_0::KeyPurpose::ENCRYPT,
    ::android::hardware::keymaster::V3_0::KeyPurpose::DECRYPT,
    ::android::hardware::keymaster::V3_0::KeyPurpose::SIGN,
    ::android::hardware::keymaster::V3_0::KeyPurpose::VERIFY,
    ::android::hardware::keymaster::V3_0::KeyPurpose::DERIVE_KEY,
    ::android::hardware::keymaster::V3_0::KeyPurpose::WRAP_KEY,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::ErrorCode, 68> hidl_enum_values<::android::hardware::keymaster::V3_0::ErrorCode> = {
    ::android::hardware::keymaster::V3_0::ErrorCode::OK,
    ::android::hardware::keymaster::V3_0::ErrorCode::ROOT_OF_TRUST_ALREADY_SET,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_PURPOSE,
    ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_PURPOSE,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_ALGORITHM,
    ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_ALGORITHM,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_SIZE,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_BLOCK_MODE,
    ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_BLOCK_MODE,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_MAC_LENGTH,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_PADDING_MODE,
    ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_PADDING_MODE,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_DIGEST,
    ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_DIGEST,
    ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_EXPIRATION_TIME,
    ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_USER_ID,
    ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_AUTHORIZATION_TIMEOUT,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_FORMAT,
    ::android::hardware::keymaster::V3_0::ErrorCode::INCOMPATIBLE_KEY_FORMAT,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_ENCRYPTION_ALGORITHM,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KEY_VERIFICATION_ALGORITHM,
    ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_INPUT_LENGTH,
    ::android::hardware::keymaster::V3_0::ErrorCode::KEY_EXPORT_OPTIONS_INVALID,
    ::android::hardware::keymaster::V3_0::ErrorCode::DELEGATION_NOT_ALLOWED,
    ::android::hardware::keymaster::V3_0::ErrorCode::KEY_NOT_YET_VALID,
    ::android::hardware::keymaster::V3_0::ErrorCode::KEY_EXPIRED,
    ::android::hardware::keymaster::V3_0::ErrorCode::KEY_USER_NOT_AUTHENTICATED,
    ::android::hardware::keymaster::V3_0::ErrorCode::OUTPUT_PARAMETER_NULL,
    ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_OPERATION_HANDLE,
    ::android::hardware::keymaster::V3_0::ErrorCode::INSUFFICIENT_BUFFER_SPACE,
    ::android::hardware::keymaster::V3_0::ErrorCode::VERIFICATION_FAILED,
    ::android::hardware::keymaster::V3_0::ErrorCode::TOO_MANY_OPERATIONS,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNEXPECTED_NULL_POINTER,
    ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_KEY_BLOB,
    ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_NOT_ENCRYPTED,
    ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_DECRYPTION_FAILED,
    ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_NOT_SIGNED,
    ::android::hardware::keymaster::V3_0::ErrorCode::IMPORTED_KEY_VERIFICATION_FAILED,
    ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_ARGUMENT,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_TAG,
    ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_TAG,
    ::android::hardware::keymaster::V3_0::ErrorCode::MEMORY_ALLOCATION_FAILED,
    ::android::hardware::keymaster::V3_0::ErrorCode::IMPORT_PARAMETER_MISMATCH,
    ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_ACCESS_DENIED,
    ::android::hardware::keymaster::V3_0::ErrorCode::OPERATION_CANCELLED,
    ::android::hardware::keymaster::V3_0::ErrorCode::CONCURRENT_ACCESS_CONFLICT,
    ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_BUSY,
    ::android::hardware::keymaster::V3_0::ErrorCode::SECURE_HW_COMMUNICATION_FAILED,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_EC_FIELD,
    ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_NONCE,
    ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_NONCE,
    ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_MAC_LENGTH,
    ::android::hardware::keymaster::V3_0::ErrorCode::KEY_RATE_LIMIT_EXCEEDED,
    ::android::hardware::keymaster::V3_0::ErrorCode::CALLER_NONCE_PROHIBITED,
    ::android::hardware::keymaster::V3_0::ErrorCode::KEY_MAX_OPS_EXCEEDED,
    ::android::hardware::keymaster::V3_0::ErrorCode::INVALID_MAC_LENGTH,
    ::android::hardware::keymaster::V3_0::ErrorCode::MISSING_MIN_MAC_LENGTH,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_KDF,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNSUPPORTED_EC_CURVE,
    ::android::hardware::keymaster::V3_0::ErrorCode::KEY_REQUIRES_UPGRADE,
    ::android::hardware::keymaster::V3_0::ErrorCode::ATTESTATION_CHALLENGE_MISSING,
    ::android::hardware::keymaster::V3_0::ErrorCode::KEYMASTER_NOT_CONFIGURED,
    ::android::hardware::keymaster::V3_0::ErrorCode::ATTESTATION_APPLICATION_ID_MISSING,
    ::android::hardware::keymaster::V3_0::ErrorCode::CANNOT_ATTEST_IDS,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNIMPLEMENTED,
    ::android::hardware::keymaster::V3_0::ErrorCode::VERSION_MISMATCH,
    ::android::hardware::keymaster::V3_0::ErrorCode::UNKNOWN_ERROR,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::KeyDerivationFunction, 6> hidl_enum_values<::android::hardware::keymaster::V3_0::KeyDerivationFunction> = {
    ::android::hardware::keymaster::V3_0::KeyDerivationFunction::NONE,
    ::android::hardware::keymaster::V3_0::KeyDerivationFunction::RFC5869_SHA256,
    ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA1,
    ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF1_SHA256,
    ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA1,
    ::android::hardware::keymaster::V3_0::KeyDerivationFunction::ISO18033_2_KDF2_SHA256,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::HardwareAuthenticatorType, 4> hidl_enum_values<::android::hardware::keymaster::V3_0::HardwareAuthenticatorType> = {
    ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::NONE,
    ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::PASSWORD,
    ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::FINGERPRINT,
    ::android::hardware::keymaster::V3_0::HardwareAuthenticatorType::ANY,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::SecurityLevel, 2> hidl_enum_values<::android::hardware::keymaster::V3_0::SecurityLevel> = {
    ::android::hardware::keymaster::V3_0::SecurityLevel::SOFTWARE,
    ::android::hardware::keymaster::V3_0::SecurityLevel::TRUSTED_ENVIRONMENT,
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
template<> inline constexpr std::array<::android::hardware::keymaster::V3_0::KeyFormat, 3> hidl_enum_values<::android::hardware::keymaster::V3_0::KeyFormat> = {
    ::android::hardware::keymaster::V3_0::KeyFormat::X509,
    ::android::hardware::keymaster::V3_0::KeyFormat::PKCS8,
    ::android::hardware::keymaster::V3_0::KeyFormat::RAW,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V3_0_TYPES_H
