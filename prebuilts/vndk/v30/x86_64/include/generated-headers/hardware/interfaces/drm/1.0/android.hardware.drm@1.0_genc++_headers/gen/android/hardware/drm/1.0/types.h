#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;
enum class EventType : uint32_t;
enum class KeyType : uint32_t;
enum class KeyRequestType : uint32_t;
enum class KeyStatusType : uint32_t;
struct KeyStatus;
struct KeyValue;
struct SecureStop;
enum class Mode : uint32_t;
struct SubSample;
struct Pattern;
enum class BufferType : uint32_t;
struct SharedBuffer;
struct DestinationBuffer;

enum class Status : uint32_t {
    /**
     * The DRM plugin must return OK when an operation completes without any
     * errors.
     */
    OK = 0u,
    /**
     * The DRM plugin must return ERROR_DRM_NO_LICENSE, when decryption is
     * attempted and no license keys have been provided.
     */
    ERROR_DRM_NO_LICENSE = 1u /* ::android::hardware::drm::V1_0::Status.OK implicitly + 1 */,
    /**
     * ERROR_DRM_LICENSE_EXPIRED must be returned when an attempt is made
     * to use a license and the keys in that license have expired.
     */
    ERROR_DRM_LICENSE_EXPIRED = 2u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_NO_LICENSE implicitly + 1 */,
    /**
     * The DRM plugin must return ERROR_DRM_SESSION_NOT_OPENED when an
     * attempt is made to use a session that has not been opened.
     */
    ERROR_DRM_SESSION_NOT_OPENED = 3u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_LICENSE_EXPIRED implicitly + 1 */,
    /**
     * The DRM plugin must return ERROR_DRM_CANNOT_HANDLE when an unsupported
     * data format or operation is attempted.
     */
    ERROR_DRM_CANNOT_HANDLE = 4u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_SESSION_NOT_OPENED implicitly + 1 */,
    /**
     * ERROR_DRM_INVALID_STATE must be returned when the device is in a state
     * where it is not able to perform decryption.
     */
    ERROR_DRM_INVALID_STATE = 5u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_CANNOT_HANDLE implicitly + 1 */,
    /**
     * The DRM plugin must return BAD_VALUE whenever an illegal parameter is
     * passed to one of the interface functions.
     */
    BAD_VALUE = 6u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_INVALID_STATE implicitly + 1 */,
    /**
     * The DRM plugin must return ERROR_DRM_NOT_PROVISIONED from getKeyRequest,
     * openSession or provideKeyResponse when the device has not yet been
     * provisioned.
     */
    ERROR_DRM_NOT_PROVISIONED = 7u /* ::android::hardware::drm::V1_0::Status.BAD_VALUE implicitly + 1 */,
    /**
     * ERROR_DRM_RESOURCE_BUSY must be returned when resources, such as drm
     * sessions or secure buffers are not available to perform a requested
     * operation because they are already in use.
     */
    ERROR_DRM_RESOURCE_BUSY = 8u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_NOT_PROVISIONED implicitly + 1 */,
    /**
     * The DRM Plugin must return ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION
     * when the output protection level enabled on the device is not
     * sufficient to meet the requirements in the license policy.  HDCP is an
     * example of a form of output protection.
     */
    ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION = 9u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_RESOURCE_BUSY implicitly + 1 */,
    /**
     * The DRM Plugin must return ERROR_DRM_DEVICE_REVOKED from
     * provideProvisionResponse and provideKeyResponse if the response indicates
     * that the device has been revoked. Device revocation means that the device
     * is no longer permitted to play content.
     */
    ERROR_DRM_DEVICE_REVOKED = 10u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION implicitly + 1 */,
    /**
     * The DRM Plugin must return ERROR_DRM_DECRYPT if the CryptoPlugin
     * decrypt operation fails.
     */
    ERROR_DRM_DECRYPT = 11u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_DEVICE_REVOKED implicitly + 1 */,
    /**
     * ERROR_DRM_UNKNOWN must be returned when a fatal failure occurs and no
     * other defined error is appropriate.
     */
    ERROR_DRM_UNKNOWN = 12u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_DECRYPT implicitly + 1 */,
};

/**
 * EventType enumerates the events that can be delivered by sendEvent
 */
enum class EventType : uint32_t {
    /**
     * This event type indicates that the app needs to request a certificate
     * from the provisioning server. The request message data is obtained using
     * getProvisionRequest().
     */
    PROVISION_REQUIRED = 0u,
    /**
     * This event type indicates that the app needs to request keys from a
     * license server. The request message data is obtained using getKeyRequest.
     */
    KEY_NEEDED = 1u /* ::android::hardware::drm::V1_0::EventType.PROVISION_REQUIRED implicitly + 1 */,
    /**
     * This event type indicates that the licensed usage duration for keys in a
     * session has expired. The keys are no longer valid.
     */
    KEY_EXPIRED = 2u /* ::android::hardware::drm::V1_0::EventType.KEY_NEEDED implicitly + 1 */,
    /**
     * This event may indicate some specific vendor-defined condition, see your
     * DRM provider documentation for details.
     */
    VENDOR_DEFINED = 3u /* ::android::hardware::drm::V1_0::EventType.KEY_EXPIRED implicitly + 1 */,
    /**
     * This event indicates that a session opened by the app has been reclaimed
     * by the resource manager.
     */
    SESSION_RECLAIMED = 4u /* ::android::hardware::drm::V1_0::EventType.VENDOR_DEFINED implicitly + 1 */,
};

enum class KeyType : uint32_t {
    /**
     * Drm keys can be for offline content or for online streaming.
     * Offline keys are persisted on the device and may be used when the device
     * is disconnected from the network.
     */
    OFFLINE = 0u,
    /**
     * Keys for streaming are not persisted and require the device to be
     * connected to the network for periodic renewal.
     */
    STREAMING = 1u /* ::android::hardware::drm::V1_0::KeyType.OFFLINE implicitly + 1 */,
    /**
     * The Release type is used to request that offline keys be no longer
     * restricted to offline use.
     */
    RELEASE = 2u /* ::android::hardware::drm::V1_0::KeyType.STREAMING implicitly + 1 */,
};

/**
 * Enumerate KeyRequestTypes to allow an app to determine the type of a key
 * request returned from getKeyRequest.
 */
enum class KeyRequestType : uint32_t {
    /**
     * Key request type is for an initial license request
     */
    INITIAL = 0u,
    /**
     * Key request type is for license renewal. Renewal requests are used
     * to extend the validity period for streaming keys.
     */
    RENEWAL = 1u /* ::android::hardware::drm::V1_0::KeyRequestType.INITIAL implicitly + 1 */,
    /**
     * Key request type is a release. A key release causes offline keys
     * to become available for streaming.
     */
    RELEASE = 2u /* ::android::hardware::drm::V1_0::KeyRequestType.RENEWAL implicitly + 1 */,
    /**
     * Key request type is unknown due to some error condition.
     */
    UNKNOWN = 3u /* ::android::hardware::drm::V1_0::KeyRequestType.RELEASE implicitly + 1 */,
};

/**
 * Enumerate KeyStatusTypes which indicate the state of a key
 */
enum class KeyStatusType : uint32_t {
    /**
     * The key is currently usable to decrypt media data.
     */
    USABLE = 0u,
    /**
     * The key is no longer usable to decrypt media data because its expiration
     * time has passed.
     */
    EXPIRED = 1u /* ::android::hardware::drm::V1_0::KeyStatusType.USABLE implicitly + 1 */,
    /**
     * The key is not currently usable to decrypt media data because its output
     * requirements cannot currently be met.
     */
    OUTPUTNOTALLOWED = 2u /* ::android::hardware::drm::V1_0::KeyStatusType.EXPIRED implicitly + 1 */,
    /**
     * The status of the key is not yet known and is being determined.
     */
    STATUSPENDING = 3u /* ::android::hardware::drm::V1_0::KeyStatusType.OUTPUTNOTALLOWED implicitly + 1 */,
    /**
     * The key is not currently usable to decrypt media data because of an
     * internal error in processing unrelated to input parameters.
     */
    INTERNALERROR = 4u /* ::android::hardware::drm::V1_0::KeyStatusType.STATUSPENDING implicitly + 1 */,
};

typedef ::android::hardware::hidl_vec<uint8_t> SessionId;

/**
 * Used by sendKeysChange to report the usability status of each key to the
 * app.
 */
struct KeyStatus final {
    ::android::hardware::hidl_vec<uint8_t> keyId __attribute__ ((aligned(8)));
    ::android::hardware::drm::V1_0::KeyStatusType type __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::drm::V1_0::KeyStatus, keyId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::drm::V1_0::KeyStatus, type) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::drm::V1_0::KeyStatus) == 24, "wrong size");
static_assert(__alignof(::android::hardware::drm::V1_0::KeyStatus) == 8, "wrong alignment");

/**
 * Simulates a KeyedVector<String8, String8>
 */
struct KeyValue final {
    ::android::hardware::hidl_string key __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::drm::V1_0::KeyValue, key) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::drm::V1_0::KeyValue, value) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::drm::V1_0::KeyValue) == 32, "wrong size");
static_assert(__alignof(::android::hardware::drm::V1_0::KeyValue) == 8, "wrong alignment");

typedef ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::KeyValue> KeyedVector;

/**
 * Encapsulates a secure stop opaque object
 */
struct SecureStop final {
    ::android::hardware::hidl_vec<uint8_t> opaqueData __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::drm::V1_0::SecureStop, opaqueData) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::drm::V1_0::SecureStop) == 16, "wrong size");
static_assert(__alignof(::android::hardware::drm::V1_0::SecureStop) == 8, "wrong alignment");

typedef ::android::hardware::hidl_vec<uint8_t> SecureStopId;

/**
 * Enumerate the supported crypto modes
 */
enum class Mode : uint32_t {
    UNENCRYPTED = 0u,
    AES_CTR = 1u,
    AES_CBC_CTS = 2u,
    AES_CBC = 3u,
};

/**
 * A subsample consists of some number of bytes of clear (unencrypted)
 * data followed by a number of bytes of encrypted data.
 */
struct SubSample final {
    uint32_t numBytesOfClearData __attribute__ ((aligned(4)));
    uint32_t numBytesOfEncryptedData __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::drm::V1_0::SubSample, numBytesOfClearData) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::drm::V1_0::SubSample, numBytesOfEncryptedData) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::drm::V1_0::SubSample) == 8, "wrong size");
static_assert(__alignof(::android::hardware::drm::V1_0::SubSample) == 4, "wrong alignment");

/**
 * A crypto Pattern is a repeating sequence of encrypted and clear blocks
 * occuring within the bytes indicated by mNumBytesOfEncryptedDatad bytes
 * of a subsample. Patterns are used to reduce the CPU overhead of
 * decrypting samples. As an example, HLS uses 1:9 patterns where every
 * 10th block is encrypted.
 */
struct Pattern final {
    /**
     * The number of blocks to be encrypted in the pattern. If zero,
     * pattern encryption is inoperative.
     */
    uint32_t encryptBlocks __attribute__ ((aligned(4)));
    /**
     * The number of blocks to be skipped (left clear) in the pattern. If
     * zero, pattern encryption is inoperative.
     */
    uint32_t skipBlocks __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::drm::V1_0::Pattern, encryptBlocks) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::drm::V1_0::Pattern, skipBlocks) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::drm::V1_0::Pattern) == 8, "wrong size");
static_assert(__alignof(::android::hardware::drm::V1_0::Pattern) == 4, "wrong alignment");

enum class BufferType : uint32_t {
    SHARED_MEMORY = 0u,
    NATIVE_HANDLE = 1u,
};

/**
 * SharedBuffer describes a decrypt buffer which is defined by a bufferId, an
 * offset and a size.  The offset is relative to the shared memory base for the
 * memory region identified by bufferId, which is established by
 * setSharedMemoryBase().
 */
struct SharedBuffer final {
    /**
     * The unique buffer identifier
     */
    uint32_t bufferId __attribute__ ((aligned(4)));
    /**
     * The offset from the shared memory base
     */
    uint64_t offset __attribute__ ((aligned(8)));
    /**
     * The size of the shared buffer in bytes
     */
    uint64_t size __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::drm::V1_0::SharedBuffer, bufferId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::drm::V1_0::SharedBuffer, offset) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::drm::V1_0::SharedBuffer, size) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::drm::V1_0::SharedBuffer) == 24, "wrong size");
static_assert(__alignof(::android::hardware::drm::V1_0::SharedBuffer) == 8, "wrong alignment");

/**
 * A decrypt destination buffer can be either normal user-space shared
 * memory for the non-secure decrypt case, or it can be a secure buffer
 * which is referenced by a native-handle. The native handle is allocated
 * by the vendor's buffer allocator.
 */
struct DestinationBuffer final {
    /**
     * The type of the buffer
     */
    ::android::hardware::drm::V1_0::BufferType type __attribute__ ((aligned(4)));
    /**
     * If type == SHARED_MEMORY, the decrypted data must be written
     * to user-space non-secure shared memory.
     */
    ::android::hardware::drm::V1_0::SharedBuffer nonsecureMemory __attribute__ ((aligned(8)));
    /**
     * If type == NATIVE_HANDLE, the decrypted data must be written
     * to secure memory referenced by the vendor's buffer allocator.
     */
    ::android::hardware::hidl_handle secureMemory __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::drm::V1_0::DestinationBuffer, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::drm::V1_0::DestinationBuffer, nonsecureMemory) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::drm::V1_0::DestinationBuffer, secureMemory) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::drm::V1_0::DestinationBuffer) == 48, "wrong size");
static_assert(__alignof(::android::hardware::drm::V1_0::DestinationBuffer) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_0::Status o);
static inline void PrintTo(::android::hardware::drm::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::Status lhs, const ::android::hardware::drm::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::Status lhs, const ::android::hardware::drm::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_0::EventType o);
static inline void PrintTo(::android::hardware::drm::V1_0::EventType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::EventType lhs, const ::android::hardware::drm::V1_0::EventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_0::EventType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::EventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::EventType lhs, const ::android::hardware::drm::V1_0::EventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_0::EventType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::EventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_0::EventType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_0::EventType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_0::KeyType o);
static inline void PrintTo(::android::hardware::drm::V1_0::KeyType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::KeyType lhs, const ::android::hardware::drm::V1_0::KeyType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_0::KeyType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::KeyType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::KeyType lhs, const ::android::hardware::drm::V1_0::KeyType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_0::KeyType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::KeyType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_0::KeyType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_0::KeyType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_0::KeyRequestType o);
static inline void PrintTo(::android::hardware::drm::V1_0::KeyRequestType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::KeyRequestType lhs, const ::android::hardware::drm::V1_0::KeyRequestType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_0::KeyRequestType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::KeyRequestType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::KeyRequestType lhs, const ::android::hardware::drm::V1_0::KeyRequestType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_0::KeyRequestType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::KeyRequestType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_0::KeyRequestType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_0::KeyRequestType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_0::KeyStatusType o);
static inline void PrintTo(::android::hardware::drm::V1_0::KeyStatusType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::KeyStatusType lhs, const ::android::hardware::drm::V1_0::KeyStatusType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_0::KeyStatusType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::KeyStatusType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::KeyStatusType lhs, const ::android::hardware::drm::V1_0::KeyStatusType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_0::KeyStatusType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::KeyStatusType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_0::KeyStatusType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_0::KeyStatusType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::drm::V1_0::KeyStatus& o);
static inline void PrintTo(const ::android::hardware::drm::V1_0::KeyStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_0::KeyStatus& lhs, const ::android::hardware::drm::V1_0::KeyStatus& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_0::KeyStatus& lhs, const ::android::hardware::drm::V1_0::KeyStatus& rhs);

static inline std::string toString(const ::android::hardware::drm::V1_0::KeyValue& o);
static inline void PrintTo(const ::android::hardware::drm::V1_0::KeyValue& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_0::KeyValue& lhs, const ::android::hardware::drm::V1_0::KeyValue& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_0::KeyValue& lhs, const ::android::hardware::drm::V1_0::KeyValue& rhs);

static inline std::string toString(const ::android::hardware::drm::V1_0::SecureStop& o);
static inline void PrintTo(const ::android::hardware::drm::V1_0::SecureStop& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_0::SecureStop& lhs, const ::android::hardware::drm::V1_0::SecureStop& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_0::SecureStop& lhs, const ::android::hardware::drm::V1_0::SecureStop& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_0::Mode o);
static inline void PrintTo(::android::hardware::drm::V1_0::Mode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::Mode lhs, const ::android::hardware::drm::V1_0::Mode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_0::Mode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::Mode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::Mode lhs, const ::android::hardware::drm::V1_0::Mode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_0::Mode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::Mode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_0::Mode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_0::Mode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::drm::V1_0::SubSample& o);
static inline void PrintTo(const ::android::hardware::drm::V1_0::SubSample& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_0::SubSample& lhs, const ::android::hardware::drm::V1_0::SubSample& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_0::SubSample& lhs, const ::android::hardware::drm::V1_0::SubSample& rhs);

static inline std::string toString(const ::android::hardware::drm::V1_0::Pattern& o);
static inline void PrintTo(const ::android::hardware::drm::V1_0::Pattern& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_0::Pattern& lhs, const ::android::hardware::drm::V1_0::Pattern& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_0::Pattern& lhs, const ::android::hardware::drm::V1_0::Pattern& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_0::BufferType o);
static inline void PrintTo(::android::hardware::drm::V1_0::BufferType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::BufferType lhs, const ::android::hardware::drm::V1_0::BufferType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_0::BufferType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_0::BufferType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::BufferType lhs, const ::android::hardware::drm::V1_0::BufferType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_0::BufferType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_0::BufferType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_0::BufferType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_0::BufferType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::drm::V1_0::SharedBuffer& o);
static inline void PrintTo(const ::android::hardware::drm::V1_0::SharedBuffer& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_0::SharedBuffer& lhs, const ::android::hardware::drm::V1_0::SharedBuffer& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_0::SharedBuffer& lhs, const ::android::hardware::drm::V1_0::SharedBuffer& rhs);

static inline std::string toString(const ::android::hardware::drm::V1_0::DestinationBuffer& o);
static inline void PrintTo(const ::android::hardware::drm::V1_0::DestinationBuffer& o, ::std::ostream*);
// operator== and operator!= are not generated for DestinationBuffer

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::drm::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_0::Status::OK) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::OK;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_NO_LICENSE) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_NO_LICENSE)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_NO_LICENSE";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_NO_LICENSE;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_LICENSE_EXPIRED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_LICENSE_EXPIRED)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_LICENSE_EXPIRED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_LICENSE_EXPIRED;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_SESSION_NOT_OPENED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_SESSION_NOT_OPENED)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_SESSION_NOT_OPENED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_SESSION_NOT_OPENED;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_CANNOT_HANDLE) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_CANNOT_HANDLE)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_CANNOT_HANDLE";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_CANNOT_HANDLE;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_INVALID_STATE) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_INVALID_STATE;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::BAD_VALUE) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "BAD_VALUE";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::BAD_VALUE;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_NOT_PROVISIONED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_NOT_PROVISIONED)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_NOT_PROVISIONED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_NOT_PROVISIONED;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_RESOURCE_BUSY) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_RESOURCE_BUSY)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_RESOURCE_BUSY";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_RESOURCE_BUSY;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_DEVICE_REVOKED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_DEVICE_REVOKED)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_DEVICE_REVOKED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_DEVICE_REVOKED;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_DECRYPT) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_DECRYPT)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_DECRYPT";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_DECRYPT;
    }
    if ((o & ::android::hardware::drm::V1_0::Status::ERROR_DRM_UNKNOWN) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Status::ERROR_DRM_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Status::ERROR_DRM_UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_0::Status::OK) {
        return "OK";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_NO_LICENSE) {
        return "ERROR_DRM_NO_LICENSE";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_LICENSE_EXPIRED) {
        return "ERROR_DRM_LICENSE_EXPIRED";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_SESSION_NOT_OPENED) {
        return "ERROR_DRM_SESSION_NOT_OPENED";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_CANNOT_HANDLE) {
        return "ERROR_DRM_CANNOT_HANDLE";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_INVALID_STATE) {
        return "ERROR_DRM_INVALID_STATE";
    }
    if (o == ::android::hardware::drm::V1_0::Status::BAD_VALUE) {
        return "BAD_VALUE";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_NOT_PROVISIONED) {
        return "ERROR_DRM_NOT_PROVISIONED";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_RESOURCE_BUSY) {
        return "ERROR_DRM_RESOURCE_BUSY";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION) {
        return "ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_DEVICE_REVOKED) {
        return "ERROR_DRM_DEVICE_REVOKED";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_DECRYPT) {
        return "ERROR_DRM_DECRYPT";
    }
    if (o == ::android::hardware::drm::V1_0::Status::ERROR_DRM_UNKNOWN) {
        return "ERROR_DRM_UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::drm::V1_0::EventType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_0::EventType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_0::EventType::PROVISION_REQUIRED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::EventType::PROVISION_REQUIRED)) {
        os += (first ? "" : " | ");
        os += "PROVISION_REQUIRED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::EventType::PROVISION_REQUIRED;
    }
    if ((o & ::android::hardware::drm::V1_0::EventType::KEY_NEEDED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::EventType::KEY_NEEDED)) {
        os += (first ? "" : " | ");
        os += "KEY_NEEDED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::EventType::KEY_NEEDED;
    }
    if ((o & ::android::hardware::drm::V1_0::EventType::KEY_EXPIRED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::EventType::KEY_EXPIRED)) {
        os += (first ? "" : " | ");
        os += "KEY_EXPIRED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::EventType::KEY_EXPIRED;
    }
    if ((o & ::android::hardware::drm::V1_0::EventType::VENDOR_DEFINED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::EventType::VENDOR_DEFINED)) {
        os += (first ? "" : " | ");
        os += "VENDOR_DEFINED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::EventType::VENDOR_DEFINED;
    }
    if ((o & ::android::hardware::drm::V1_0::EventType::SESSION_RECLAIMED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::EventType::SESSION_RECLAIMED)) {
        os += (first ? "" : " | ");
        os += "SESSION_RECLAIMED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::EventType::SESSION_RECLAIMED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_0::EventType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_0::EventType::PROVISION_REQUIRED) {
        return "PROVISION_REQUIRED";
    }
    if (o == ::android::hardware::drm::V1_0::EventType::KEY_NEEDED) {
        return "KEY_NEEDED";
    }
    if (o == ::android::hardware::drm::V1_0::EventType::KEY_EXPIRED) {
        return "KEY_EXPIRED";
    }
    if (o == ::android::hardware::drm::V1_0::EventType::VENDOR_DEFINED) {
        return "VENDOR_DEFINED";
    }
    if (o == ::android::hardware::drm::V1_0::EventType::SESSION_RECLAIMED) {
        return "SESSION_RECLAIMED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_0::EventType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::drm::V1_0::KeyType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_0::KeyType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_0::KeyType::OFFLINE) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyType::OFFLINE)) {
        os += (first ? "" : " | ");
        os += "OFFLINE";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyType::OFFLINE;
    }
    if ((o & ::android::hardware::drm::V1_0::KeyType::STREAMING) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyType::STREAMING)) {
        os += (first ? "" : " | ");
        os += "STREAMING";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyType::STREAMING;
    }
    if ((o & ::android::hardware::drm::V1_0::KeyType::RELEASE) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyType::RELEASE)) {
        os += (first ? "" : " | ");
        os += "RELEASE";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyType::RELEASE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_0::KeyType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_0::KeyType::OFFLINE) {
        return "OFFLINE";
    }
    if (o == ::android::hardware::drm::V1_0::KeyType::STREAMING) {
        return "STREAMING";
    }
    if (o == ::android::hardware::drm::V1_0::KeyType::RELEASE) {
        return "RELEASE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_0::KeyType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::drm::V1_0::KeyRequestType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_0::KeyRequestType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_0::KeyRequestType::INITIAL) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyRequestType::INITIAL)) {
        os += (first ? "" : " | ");
        os += "INITIAL";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyRequestType::INITIAL;
    }
    if ((o & ::android::hardware::drm::V1_0::KeyRequestType::RENEWAL) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyRequestType::RENEWAL)) {
        os += (first ? "" : " | ");
        os += "RENEWAL";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyRequestType::RENEWAL;
    }
    if ((o & ::android::hardware::drm::V1_0::KeyRequestType::RELEASE) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyRequestType::RELEASE)) {
        os += (first ? "" : " | ");
        os += "RELEASE";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyRequestType::RELEASE;
    }
    if ((o & ::android::hardware::drm::V1_0::KeyRequestType::UNKNOWN) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyRequestType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyRequestType::UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_0::KeyRequestType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_0::KeyRequestType::INITIAL) {
        return "INITIAL";
    }
    if (o == ::android::hardware::drm::V1_0::KeyRequestType::RENEWAL) {
        return "RENEWAL";
    }
    if (o == ::android::hardware::drm::V1_0::KeyRequestType::RELEASE) {
        return "RELEASE";
    }
    if (o == ::android::hardware::drm::V1_0::KeyRequestType::UNKNOWN) {
        return "UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_0::KeyRequestType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::drm::V1_0::KeyStatusType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_0::KeyStatusType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_0::KeyStatusType::USABLE) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyStatusType::USABLE)) {
        os += (first ? "" : " | ");
        os += "USABLE";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyStatusType::USABLE;
    }
    if ((o & ::android::hardware::drm::V1_0::KeyStatusType::EXPIRED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyStatusType::EXPIRED)) {
        os += (first ? "" : " | ");
        os += "EXPIRED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyStatusType::EXPIRED;
    }
    if ((o & ::android::hardware::drm::V1_0::KeyStatusType::OUTPUTNOTALLOWED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyStatusType::OUTPUTNOTALLOWED)) {
        os += (first ? "" : " | ");
        os += "OUTPUTNOTALLOWED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyStatusType::OUTPUTNOTALLOWED;
    }
    if ((o & ::android::hardware::drm::V1_0::KeyStatusType::STATUSPENDING) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyStatusType::STATUSPENDING)) {
        os += (first ? "" : " | ");
        os += "STATUSPENDING";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyStatusType::STATUSPENDING;
    }
    if ((o & ::android::hardware::drm::V1_0::KeyStatusType::INTERNALERROR) == static_cast<uint32_t>(::android::hardware::drm::V1_0::KeyStatusType::INTERNALERROR)) {
        os += (first ? "" : " | ");
        os += "INTERNALERROR";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::KeyStatusType::INTERNALERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_0::KeyStatusType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_0::KeyStatusType::USABLE) {
        return "USABLE";
    }
    if (o == ::android::hardware::drm::V1_0::KeyStatusType::EXPIRED) {
        return "EXPIRED";
    }
    if (o == ::android::hardware::drm::V1_0::KeyStatusType::OUTPUTNOTALLOWED) {
        return "OUTPUTNOTALLOWED";
    }
    if (o == ::android::hardware::drm::V1_0::KeyStatusType::STATUSPENDING) {
        return "STATUSPENDING";
    }
    if (o == ::android::hardware::drm::V1_0::KeyStatusType::INTERNALERROR) {
        return "INTERNALERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_0::KeyStatusType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::drm::V1_0::KeyStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".keyId = ";
    os += ::android::hardware::toString(o.keyId);
    os += ", .type = ";
    os += ::android::hardware::drm::V1_0::toString(o.type);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_0::KeyStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_0::KeyStatus& lhs, const ::android::hardware::drm::V1_0::KeyStatus& rhs) {
    if (lhs.keyId != rhs.keyId) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_0::KeyStatus& lhs, const ::android::hardware::drm::V1_0::KeyStatus& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::drm::V1_0::KeyValue& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".key = ";
    os += ::android::hardware::toString(o.key);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_0::KeyValue& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_0::KeyValue& lhs, const ::android::hardware::drm::V1_0::KeyValue& rhs) {
    if (lhs.key != rhs.key) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_0::KeyValue& lhs, const ::android::hardware::drm::V1_0::KeyValue& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::drm::V1_0::SecureStop& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".opaqueData = ";
    os += ::android::hardware::toString(o.opaqueData);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_0::SecureStop& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_0::SecureStop& lhs, const ::android::hardware::drm::V1_0::SecureStop& rhs) {
    if (lhs.opaqueData != rhs.opaqueData) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_0::SecureStop& lhs, const ::android::hardware::drm::V1_0::SecureStop& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::drm::V1_0::Mode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_0::Mode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_0::Mode::UNENCRYPTED) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Mode::UNENCRYPTED)) {
        os += (first ? "" : " | ");
        os += "UNENCRYPTED";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Mode::UNENCRYPTED;
    }
    if ((o & ::android::hardware::drm::V1_0::Mode::AES_CTR) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Mode::AES_CTR)) {
        os += (first ? "" : " | ");
        os += "AES_CTR";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Mode::AES_CTR;
    }
    if ((o & ::android::hardware::drm::V1_0::Mode::AES_CBC_CTS) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Mode::AES_CBC_CTS)) {
        os += (first ? "" : " | ");
        os += "AES_CBC_CTS";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Mode::AES_CBC_CTS;
    }
    if ((o & ::android::hardware::drm::V1_0::Mode::AES_CBC) == static_cast<uint32_t>(::android::hardware::drm::V1_0::Mode::AES_CBC)) {
        os += (first ? "" : " | ");
        os += "AES_CBC";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::Mode::AES_CBC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_0::Mode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_0::Mode::UNENCRYPTED) {
        return "UNENCRYPTED";
    }
    if (o == ::android::hardware::drm::V1_0::Mode::AES_CTR) {
        return "AES_CTR";
    }
    if (o == ::android::hardware::drm::V1_0::Mode::AES_CBC_CTS) {
        return "AES_CBC_CTS";
    }
    if (o == ::android::hardware::drm::V1_0::Mode::AES_CBC) {
        return "AES_CBC";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_0::Mode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::drm::V1_0::SubSample& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".numBytesOfClearData = ";
    os += ::android::hardware::toString(o.numBytesOfClearData);
    os += ", .numBytesOfEncryptedData = ";
    os += ::android::hardware::toString(o.numBytesOfEncryptedData);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_0::SubSample& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_0::SubSample& lhs, const ::android::hardware::drm::V1_0::SubSample& rhs) {
    if (lhs.numBytesOfClearData != rhs.numBytesOfClearData) {
        return false;
    }
    if (lhs.numBytesOfEncryptedData != rhs.numBytesOfEncryptedData) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_0::SubSample& lhs, const ::android::hardware::drm::V1_0::SubSample& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::drm::V1_0::Pattern& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".encryptBlocks = ";
    os += ::android::hardware::toString(o.encryptBlocks);
    os += ", .skipBlocks = ";
    os += ::android::hardware::toString(o.skipBlocks);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_0::Pattern& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_0::Pattern& lhs, const ::android::hardware::drm::V1_0::Pattern& rhs) {
    if (lhs.encryptBlocks != rhs.encryptBlocks) {
        return false;
    }
    if (lhs.skipBlocks != rhs.skipBlocks) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_0::Pattern& lhs, const ::android::hardware::drm::V1_0::Pattern& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::drm::V1_0::BufferType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_0::BufferType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_0::BufferType::SHARED_MEMORY) == static_cast<uint32_t>(::android::hardware::drm::V1_0::BufferType::SHARED_MEMORY)) {
        os += (first ? "" : " | ");
        os += "SHARED_MEMORY";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::BufferType::SHARED_MEMORY;
    }
    if ((o & ::android::hardware::drm::V1_0::BufferType::NATIVE_HANDLE) == static_cast<uint32_t>(::android::hardware::drm::V1_0::BufferType::NATIVE_HANDLE)) {
        os += (first ? "" : " | ");
        os += "NATIVE_HANDLE";
        first = false;
        flipped |= ::android::hardware::drm::V1_0::BufferType::NATIVE_HANDLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_0::BufferType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_0::BufferType::SHARED_MEMORY) {
        return "SHARED_MEMORY";
    }
    if (o == ::android::hardware::drm::V1_0::BufferType::NATIVE_HANDLE) {
        return "NATIVE_HANDLE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_0::BufferType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::drm::V1_0::SharedBuffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".bufferId = ";
    os += ::android::hardware::toString(o.bufferId);
    os += ", .offset = ";
    os += ::android::hardware::toString(o.offset);
    os += ", .size = ";
    os += ::android::hardware::toString(o.size);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_0::SharedBuffer& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_0::SharedBuffer& lhs, const ::android::hardware::drm::V1_0::SharedBuffer& rhs) {
    if (lhs.bufferId != rhs.bufferId) {
        return false;
    }
    if (lhs.offset != rhs.offset) {
        return false;
    }
    if (lhs.size != rhs.size) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_0::SharedBuffer& lhs, const ::android::hardware::drm::V1_0::SharedBuffer& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::drm::V1_0::DestinationBuffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::drm::V1_0::toString(o.type);
    os += ", .nonsecureMemory = ";
    os += ::android::hardware::drm::V1_0::toString(o.nonsecureMemory);
    os += ", .secureMemory = ";
    os += ::android::hardware::toString(o.secureMemory);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_0::DestinationBuffer& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for DestinationBuffer


}  // namespace V1_0
}  // namespace drm
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
template<> inline constexpr std::array<::android::hardware::drm::V1_0::Status, 13> hidl_enum_values<::android::hardware::drm::V1_0::Status> = {
    ::android::hardware::drm::V1_0::Status::OK,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_NO_LICENSE,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_LICENSE_EXPIRED,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_SESSION_NOT_OPENED,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_CANNOT_HANDLE,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_INVALID_STATE,
    ::android::hardware::drm::V1_0::Status::BAD_VALUE,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_NOT_PROVISIONED,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_RESOURCE_BUSY,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_DEVICE_REVOKED,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_DECRYPT,
    ::android::hardware::drm::V1_0::Status::ERROR_DRM_UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_0::EventType, 5> hidl_enum_values<::android::hardware::drm::V1_0::EventType> = {
    ::android::hardware::drm::V1_0::EventType::PROVISION_REQUIRED,
    ::android::hardware::drm::V1_0::EventType::KEY_NEEDED,
    ::android::hardware::drm::V1_0::EventType::KEY_EXPIRED,
    ::android::hardware::drm::V1_0::EventType::VENDOR_DEFINED,
    ::android::hardware::drm::V1_0::EventType::SESSION_RECLAIMED,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_0::KeyType, 3> hidl_enum_values<::android::hardware::drm::V1_0::KeyType> = {
    ::android::hardware::drm::V1_0::KeyType::OFFLINE,
    ::android::hardware::drm::V1_0::KeyType::STREAMING,
    ::android::hardware::drm::V1_0::KeyType::RELEASE,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_0::KeyRequestType, 4> hidl_enum_values<::android::hardware::drm::V1_0::KeyRequestType> = {
    ::android::hardware::drm::V1_0::KeyRequestType::INITIAL,
    ::android::hardware::drm::V1_0::KeyRequestType::RENEWAL,
    ::android::hardware::drm::V1_0::KeyRequestType::RELEASE,
    ::android::hardware::drm::V1_0::KeyRequestType::UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_0::KeyStatusType, 5> hidl_enum_values<::android::hardware::drm::V1_0::KeyStatusType> = {
    ::android::hardware::drm::V1_0::KeyStatusType::USABLE,
    ::android::hardware::drm::V1_0::KeyStatusType::EXPIRED,
    ::android::hardware::drm::V1_0::KeyStatusType::OUTPUTNOTALLOWED,
    ::android::hardware::drm::V1_0::KeyStatusType::STATUSPENDING,
    ::android::hardware::drm::V1_0::KeyStatusType::INTERNALERROR,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_0::Mode, 4> hidl_enum_values<::android::hardware::drm::V1_0::Mode> = {
    ::android::hardware::drm::V1_0::Mode::UNENCRYPTED,
    ::android::hardware::drm::V1_0::Mode::AES_CTR,
    ::android::hardware::drm::V1_0::Mode::AES_CBC_CTS,
    ::android::hardware::drm::V1_0::Mode::AES_CBC,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_0::BufferType, 2> hidl_enum_values<::android::hardware::drm::V1_0::BufferType> = {
    ::android::hardware::drm::V1_0::BufferType::SHARED_MEMORY,
    ::android::hardware::drm::V1_0::BufferType::NATIVE_HANDLE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_TYPES_H
