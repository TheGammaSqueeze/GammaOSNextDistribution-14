#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_TYPES_H

#include <android/hardware/drm/1.0/types.h>
#include <android/hardware/drm/1.1/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_2 {

// Forward declaration for forward reference support:
enum class OfflineLicenseState : uint32_t;
enum class Status : uint32_t;
enum class HdcpLevel : uint32_t;
enum class KeyStatusType : uint32_t;
struct KeyStatus;

enum class OfflineLicenseState : uint32_t {
    /**
     * Offline license state is unknown
     */
    UNKNOWN = 0u,
    /**
     * Offline license state is usable, the keys are usable for decryption.
     */
    USABLE = 1u /* ::android::hardware::drm::V1_2::OfflineLicenseState.UNKNOWN implicitly + 1 */,
    /**
     * Offline license state is inactive, the keys have been marked for
     * release using {@link #getKeyRequest} with KEY_TYPE_RELEASE but the
     * key response has not been received.
     */
    INACTIVE = 2u /* ::android::hardware::drm::V1_2::OfflineLicenseState.USABLE implicitly + 1 */,
};

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
    /**
     * The drm HAL module must return ERROR_DRM_INSUFFICIENT_SECURITY
     * from the crypto plugin decrypt method when the security level
     * of the device is not sufficient to meet the requirements in the
     * license policy.
     */
    ERROR_DRM_INSUFFICIENT_SECURITY = 13u /* ::android::hardware::drm::V1_0::Status.ERROR_DRM_UNKNOWN implicitly + 1 */,
    /**
     * The drm HAL module must return ERROR_FRAME_TOO_LARGE from the
     * decrypt method when the frame being decrypted into the secure
     * output buffer exceeds the size of the buffer.
     */
    ERROR_DRM_FRAME_TOO_LARGE = 14u /* ::android::hardware::drm::V1_2::Status.ERROR_DRM_INSUFFICIENT_SECURITY implicitly + 1 */,
    /**
     * This error must be returned from any session method when an
     * attempt is made to use the session after the crypto hardware
     * state has been invalidated. Some devices are not able to
     * retain crypto session state across device suspend/resume which
     * results in invalid session state.
     */
    ERROR_DRM_SESSION_LOST_STATE = 15u /* ::android::hardware::drm::V1_2::Status.ERROR_DRM_FRAME_TOO_LARGE implicitly + 1 */,
    /**
     * The drm HAL module must return this error if client
     * applications using the hal are temporarily exceeding the
     * capacity of available crypto resources such that a retry of
     * the operation is likely to succeed.
     */
    ERROR_DRM_RESOURCE_CONTENTION = 16u /* ::android::hardware::drm::V1_2::Status.ERROR_DRM_SESSION_LOST_STATE implicitly + 1 */,
};

/**
 * HDCP specifications are defined by Digital Content Protection LLC (DCP).
 *   "HDCP Specification Rev. 2.3 Interface Independent Adaptation"
 *   "HDCP 2.3 on HDMI Specification"
 */
enum class HdcpLevel : uint32_t {
    /**
     * Unable to determine the HDCP level
     */
    HDCP_UNKNOWN = 0u,
    /**
     * No HDCP, output is unprotected
     */
    HDCP_NONE = 1u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_UNKNOWN implicitly + 1 */,
    /**
     * HDCP version 1.0
     */
    HDCP_V1 = 2u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_NONE implicitly + 1 */,
    /**
     * HDCP version 2.0 Type 1.
     */
    HDCP_V2 = 3u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_V1 implicitly + 1 */,
    /**
     * HDCP version 2.1 Type 1.
     */
    HDCP_V2_1 = 4u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_V2 implicitly + 1 */,
    /**
     *  HDCP version 2.2 Type 1.
     */
    HDCP_V2_2 = 5u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_V2_1 implicitly + 1 */,
    /**
     * No digital output, implicitly secure
     */
    HDCP_NO_OUTPUT = 6u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_V2_2 implicitly + 1 */,
    /**
     * HDCP version 2.3 Type 1.
     */
    HDCP_V2_3 = 7u /* ::android::hardware::drm::V1_1::HdcpLevel.HDCP_NO_OUTPUT implicitly + 1 */,
};

/**
 * KeySetId is an identifier that references a set of keys in an
 * offline license. The keySetId is created by the HAL implementation
 * and returned from provideKeyResponse and getOfflineLicenseIds. The
 * framework passes KeySetId back to the HAL when referring to the key
 * set in methods that take a KeySetId as an input parameter.
 */
typedef ::android::hardware::hidl_vec<uint8_t> KeySetId;

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
    /**
     * The key is not yet usable to decrypt media because the start
     * time is in the future. The key must become usable when
     * its start time is reached.
     */
    USABLEINFUTURE = 5u /* ::android::hardware::drm::V1_0::KeyStatusType.INTERNALERROR implicitly + 1 */,
};

/**
 * Used by sendKeysChange_1_2 to report the usability status of each key to the
 * app.
 *
 * This struct only differs from @1.0 version by the addition of new
 * KeyStatusType(s).
 *
 */
struct KeyStatus final {
    ::android::hardware::hidl_vec<uint8_t> keyId __attribute__ ((aligned(8)));
    ::android::hardware::drm::V1_2::KeyStatusType type __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::drm::V1_2::KeyStatus, keyId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::drm::V1_2::KeyStatus, type) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::drm::V1_2::KeyStatus) == 24, "wrong size");
static_assert(__alignof(::android::hardware::drm::V1_2::KeyStatus) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_2::OfflineLicenseState o);
static inline void PrintTo(::android::hardware::drm::V1_2::OfflineLicenseState o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_2::OfflineLicenseState lhs, const ::android::hardware::drm::V1_2::OfflineLicenseState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_2::OfflineLicenseState rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_2::OfflineLicenseState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_2::OfflineLicenseState lhs, const ::android::hardware::drm::V1_2::OfflineLicenseState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_2::OfflineLicenseState rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_2::OfflineLicenseState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_2::OfflineLicenseState e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_2::OfflineLicenseState e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_2::Status o);
static inline void PrintTo(::android::hardware::drm::V1_2::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_2::Status lhs, const ::android::hardware::drm::V1_2::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_2::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_2::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_2::Status lhs, const ::android::hardware::drm::V1_2::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_2::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_2::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_2::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_2::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_2::HdcpLevel o);
static inline void PrintTo(::android::hardware::drm::V1_2::HdcpLevel o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_2::HdcpLevel lhs, const ::android::hardware::drm::V1_2::HdcpLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_2::HdcpLevel rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_2::HdcpLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_2::HdcpLevel lhs, const ::android::hardware::drm::V1_2::HdcpLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_2::HdcpLevel rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_2::HdcpLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_2::HdcpLevel e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_2::HdcpLevel e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::drm::V1_2::KeyStatusType o);
static inline void PrintTo(::android::hardware::drm::V1_2::KeyStatusType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::drm::V1_2::KeyStatusType lhs, const ::android::hardware::drm::V1_2::KeyStatusType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::drm::V1_2::KeyStatusType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::drm::V1_2::KeyStatusType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_2::KeyStatusType lhs, const ::android::hardware::drm::V1_2::KeyStatusType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::drm::V1_2::KeyStatusType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::drm::V1_2::KeyStatusType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::drm::V1_2::KeyStatusType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::drm::V1_2::KeyStatusType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::drm::V1_2::KeyStatus& o);
static inline void PrintTo(const ::android::hardware::drm::V1_2::KeyStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::drm::V1_2::KeyStatus& lhs, const ::android::hardware::drm::V1_2::KeyStatus& rhs);
static inline bool operator!=(const ::android::hardware::drm::V1_2::KeyStatus& lhs, const ::android::hardware::drm::V1_2::KeyStatus& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::drm::V1_2::OfflineLicenseState>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_2::OfflineLicenseState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_2::OfflineLicenseState::UNKNOWN) == static_cast<uint32_t>(::android::hardware::drm::V1_2::OfflineLicenseState::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::OfflineLicenseState::UNKNOWN;
    }
    if ((o & ::android::hardware::drm::V1_2::OfflineLicenseState::USABLE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::OfflineLicenseState::USABLE)) {
        os += (first ? "" : " | ");
        os += "USABLE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::OfflineLicenseState::USABLE;
    }
    if ((o & ::android::hardware::drm::V1_2::OfflineLicenseState::INACTIVE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::OfflineLicenseState::INACTIVE)) {
        os += (first ? "" : " | ");
        os += "INACTIVE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::OfflineLicenseState::INACTIVE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_2::OfflineLicenseState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_2::OfflineLicenseState::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::drm::V1_2::OfflineLicenseState::USABLE) {
        return "USABLE";
    }
    if (o == ::android::hardware::drm::V1_2::OfflineLicenseState::INACTIVE) {
        return "INACTIVE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_2::OfflineLicenseState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::drm::V1_2::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_2::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_2::Status::OK) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::OK;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_NO_LICENSE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_NO_LICENSE)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_NO_LICENSE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_NO_LICENSE;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_LICENSE_EXPIRED) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_LICENSE_EXPIRED)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_LICENSE_EXPIRED";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_LICENSE_EXPIRED;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_SESSION_NOT_OPENED) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_SESSION_NOT_OPENED)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_SESSION_NOT_OPENED";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_SESSION_NOT_OPENED;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_CANNOT_HANDLE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_CANNOT_HANDLE)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_CANNOT_HANDLE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_CANNOT_HANDLE;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_INVALID_STATE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_INVALID_STATE;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::BAD_VALUE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "BAD_VALUE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::BAD_VALUE;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_NOT_PROVISIONED) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_NOT_PROVISIONED)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_NOT_PROVISIONED";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_NOT_PROVISIONED;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_RESOURCE_BUSY) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_RESOURCE_BUSY)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_RESOURCE_BUSY";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_RESOURCE_BUSY;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_DEVICE_REVOKED) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_DEVICE_REVOKED)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_DEVICE_REVOKED";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_DEVICE_REVOKED;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_DECRYPT) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_DECRYPT)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_DECRYPT";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_DECRYPT;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_UNKNOWN) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_UNKNOWN;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_INSUFFICIENT_SECURITY) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_INSUFFICIENT_SECURITY)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_INSUFFICIENT_SECURITY";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_INSUFFICIENT_SECURITY;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_FRAME_TOO_LARGE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_FRAME_TOO_LARGE)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_FRAME_TOO_LARGE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_FRAME_TOO_LARGE;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_SESSION_LOST_STATE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_SESSION_LOST_STATE)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_SESSION_LOST_STATE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_SESSION_LOST_STATE;
    }
    if ((o & ::android::hardware::drm::V1_2::Status::ERROR_DRM_RESOURCE_CONTENTION) == static_cast<uint32_t>(::android::hardware::drm::V1_2::Status::ERROR_DRM_RESOURCE_CONTENTION)) {
        os += (first ? "" : " | ");
        os += "ERROR_DRM_RESOURCE_CONTENTION";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::Status::ERROR_DRM_RESOURCE_CONTENTION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_2::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_2::Status::OK) {
        return "OK";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_NO_LICENSE) {
        return "ERROR_DRM_NO_LICENSE";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_LICENSE_EXPIRED) {
        return "ERROR_DRM_LICENSE_EXPIRED";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_SESSION_NOT_OPENED) {
        return "ERROR_DRM_SESSION_NOT_OPENED";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_CANNOT_HANDLE) {
        return "ERROR_DRM_CANNOT_HANDLE";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_INVALID_STATE) {
        return "ERROR_DRM_INVALID_STATE";
    }
    if (o == ::android::hardware::drm::V1_2::Status::BAD_VALUE) {
        return "BAD_VALUE";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_NOT_PROVISIONED) {
        return "ERROR_DRM_NOT_PROVISIONED";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_RESOURCE_BUSY) {
        return "ERROR_DRM_RESOURCE_BUSY";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION) {
        return "ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_DEVICE_REVOKED) {
        return "ERROR_DRM_DEVICE_REVOKED";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_DECRYPT) {
        return "ERROR_DRM_DECRYPT";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_UNKNOWN) {
        return "ERROR_DRM_UNKNOWN";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_INSUFFICIENT_SECURITY) {
        return "ERROR_DRM_INSUFFICIENT_SECURITY";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_FRAME_TOO_LARGE) {
        return "ERROR_DRM_FRAME_TOO_LARGE";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_SESSION_LOST_STATE) {
        return "ERROR_DRM_SESSION_LOST_STATE";
    }
    if (o == ::android::hardware::drm::V1_2::Status::ERROR_DRM_RESOURCE_CONTENTION) {
        return "ERROR_DRM_RESOURCE_CONTENTION";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_2::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::drm::V1_2::HdcpLevel>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_2::HdcpLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_2::HdcpLevel::HDCP_UNKNOWN) == static_cast<uint32_t>(::android::hardware::drm::V1_2::HdcpLevel::HDCP_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "HDCP_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::HdcpLevel::HDCP_UNKNOWN;
    }
    if ((o & ::android::hardware::drm::V1_2::HdcpLevel::HDCP_NONE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::HdcpLevel::HDCP_NONE)) {
        os += (first ? "" : " | ");
        os += "HDCP_NONE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::HdcpLevel::HDCP_NONE;
    }
    if ((o & ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V1) == static_cast<uint32_t>(::android::hardware::drm::V1_2::HdcpLevel::HDCP_V1)) {
        os += (first ? "" : " | ");
        os += "HDCP_V1";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V1;
    }
    if ((o & ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2) == static_cast<uint32_t>(::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2)) {
        os += (first ? "" : " | ");
        os += "HDCP_V2";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2;
    }
    if ((o & ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_1) == static_cast<uint32_t>(::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_1)) {
        os += (first ? "" : " | ");
        os += "HDCP_V2_1";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_1;
    }
    if ((o & ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_2) == static_cast<uint32_t>(::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_2)) {
        os += (first ? "" : " | ");
        os += "HDCP_V2_2";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_2;
    }
    if ((o & ::android::hardware::drm::V1_2::HdcpLevel::HDCP_NO_OUTPUT) == static_cast<uint32_t>(::android::hardware::drm::V1_2::HdcpLevel::HDCP_NO_OUTPUT)) {
        os += (first ? "" : " | ");
        os += "HDCP_NO_OUTPUT";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::HdcpLevel::HDCP_NO_OUTPUT;
    }
    if ((o & ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_3) == static_cast<uint32_t>(::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_3)) {
        os += (first ? "" : " | ");
        os += "HDCP_V2_3";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_3;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_2::HdcpLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_2::HdcpLevel::HDCP_UNKNOWN) {
        return "HDCP_UNKNOWN";
    }
    if (o == ::android::hardware::drm::V1_2::HdcpLevel::HDCP_NONE) {
        return "HDCP_NONE";
    }
    if (o == ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V1) {
        return "HDCP_V1";
    }
    if (o == ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2) {
        return "HDCP_V2";
    }
    if (o == ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_1) {
        return "HDCP_V2_1";
    }
    if (o == ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_2) {
        return "HDCP_V2_2";
    }
    if (o == ::android::hardware::drm::V1_2::HdcpLevel::HDCP_NO_OUTPUT) {
        return "HDCP_NO_OUTPUT";
    }
    if (o == ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_3) {
        return "HDCP_V2_3";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_2::HdcpLevel o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::drm::V1_2::KeyStatusType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::drm::V1_2::KeyStatusType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::drm::V1_2::KeyStatusType::USABLE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::KeyStatusType::USABLE)) {
        os += (first ? "" : " | ");
        os += "USABLE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::KeyStatusType::USABLE;
    }
    if ((o & ::android::hardware::drm::V1_2::KeyStatusType::EXPIRED) == static_cast<uint32_t>(::android::hardware::drm::V1_2::KeyStatusType::EXPIRED)) {
        os += (first ? "" : " | ");
        os += "EXPIRED";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::KeyStatusType::EXPIRED;
    }
    if ((o & ::android::hardware::drm::V1_2::KeyStatusType::OUTPUTNOTALLOWED) == static_cast<uint32_t>(::android::hardware::drm::V1_2::KeyStatusType::OUTPUTNOTALLOWED)) {
        os += (first ? "" : " | ");
        os += "OUTPUTNOTALLOWED";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::KeyStatusType::OUTPUTNOTALLOWED;
    }
    if ((o & ::android::hardware::drm::V1_2::KeyStatusType::STATUSPENDING) == static_cast<uint32_t>(::android::hardware::drm::V1_2::KeyStatusType::STATUSPENDING)) {
        os += (first ? "" : " | ");
        os += "STATUSPENDING";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::KeyStatusType::STATUSPENDING;
    }
    if ((o & ::android::hardware::drm::V1_2::KeyStatusType::INTERNALERROR) == static_cast<uint32_t>(::android::hardware::drm::V1_2::KeyStatusType::INTERNALERROR)) {
        os += (first ? "" : " | ");
        os += "INTERNALERROR";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::KeyStatusType::INTERNALERROR;
    }
    if ((o & ::android::hardware::drm::V1_2::KeyStatusType::USABLEINFUTURE) == static_cast<uint32_t>(::android::hardware::drm::V1_2::KeyStatusType::USABLEINFUTURE)) {
        os += (first ? "" : " | ");
        os += "USABLEINFUTURE";
        first = false;
        flipped |= ::android::hardware::drm::V1_2::KeyStatusType::USABLEINFUTURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::drm::V1_2::KeyStatusType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::drm::V1_2::KeyStatusType::USABLE) {
        return "USABLE";
    }
    if (o == ::android::hardware::drm::V1_2::KeyStatusType::EXPIRED) {
        return "EXPIRED";
    }
    if (o == ::android::hardware::drm::V1_2::KeyStatusType::OUTPUTNOTALLOWED) {
        return "OUTPUTNOTALLOWED";
    }
    if (o == ::android::hardware::drm::V1_2::KeyStatusType::STATUSPENDING) {
        return "STATUSPENDING";
    }
    if (o == ::android::hardware::drm::V1_2::KeyStatusType::INTERNALERROR) {
        return "INTERNALERROR";
    }
    if (o == ::android::hardware::drm::V1_2::KeyStatusType::USABLEINFUTURE) {
        return "USABLEINFUTURE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::drm::V1_2::KeyStatusType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::drm::V1_2::KeyStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".keyId = ";
    os += ::android::hardware::toString(o.keyId);
    os += ", .type = ";
    os += ::android::hardware::drm::V1_2::toString(o.type);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::drm::V1_2::KeyStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::drm::V1_2::KeyStatus& lhs, const ::android::hardware::drm::V1_2::KeyStatus& rhs) {
    if (lhs.keyId != rhs.keyId) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::drm::V1_2::KeyStatus& lhs, const ::android::hardware::drm::V1_2::KeyStatus& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_2
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
template<> inline constexpr std::array<::android::hardware::drm::V1_2::OfflineLicenseState, 3> hidl_enum_values<::android::hardware::drm::V1_2::OfflineLicenseState> = {
    ::android::hardware::drm::V1_2::OfflineLicenseState::UNKNOWN,
    ::android::hardware::drm::V1_2::OfflineLicenseState::USABLE,
    ::android::hardware::drm::V1_2::OfflineLicenseState::INACTIVE,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_2::Status, 17> hidl_enum_values<::android::hardware::drm::V1_2::Status> = {
    ::android::hardware::drm::V1_2::Status::OK,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_NO_LICENSE,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_LICENSE_EXPIRED,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_SESSION_NOT_OPENED,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_CANNOT_HANDLE,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_INVALID_STATE,
    ::android::hardware::drm::V1_2::Status::BAD_VALUE,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_NOT_PROVISIONED,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_RESOURCE_BUSY,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_DEVICE_REVOKED,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_DECRYPT,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_UNKNOWN,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_INSUFFICIENT_SECURITY,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_FRAME_TOO_LARGE,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_SESSION_LOST_STATE,
    ::android::hardware::drm::V1_2::Status::ERROR_DRM_RESOURCE_CONTENTION,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_2::HdcpLevel, 8> hidl_enum_values<::android::hardware::drm::V1_2::HdcpLevel> = {
    ::android::hardware::drm::V1_2::HdcpLevel::HDCP_UNKNOWN,
    ::android::hardware::drm::V1_2::HdcpLevel::HDCP_NONE,
    ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V1,
    ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2,
    ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_1,
    ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_2,
    ::android::hardware::drm::V1_2::HdcpLevel::HDCP_NO_OUTPUT,
    ::android::hardware::drm::V1_2::HdcpLevel::HDCP_V2_3,
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
template<> inline constexpr std::array<::android::hardware::drm::V1_2::KeyStatusType, 6> hidl_enum_values<::android::hardware::drm::V1_2::KeyStatusType> = {
    ::android::hardware::drm::V1_2::KeyStatusType::USABLE,
    ::android::hardware::drm::V1_2::KeyStatusType::EXPIRED,
    ::android::hardware::drm::V1_2::KeyStatusType::OUTPUTNOTALLOWED,
    ::android::hardware::drm::V1_2::KeyStatusType::STATUSPENDING,
    ::android::hardware::drm::V1_2::KeyStatusType::INTERNALERROR,
    ::android::hardware::drm::V1_2::KeyStatusType::USABLEINFUTURE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_TYPES_H
