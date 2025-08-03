#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_2_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_2_TYPES_H

#include <android/hardware/cas/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace cas {
namespace V1_2 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;
enum class SessionIntent : uint32_t;
enum class ScramblingMode : uint32_t;
enum class StatusEvent : uint8_t;

enum class Status : uint32_t {
    /**
     * The CAS plugin must return OK when an operation completes without any
     * errors.
     */
    OK = 0u,
    /**
     * The CAS plugin must return ERROR_CAS_NO_LICENSE, when descrambling is
     * attempted and no license keys have been provided.
     */
    ERROR_CAS_NO_LICENSE = 1u /* ::android::hardware::cas::V1_0::Status.OK implicitly + 1 */,
    /**
     * ERROR_CAS_LICENSE_EXPIRED must be returned when an attempt is made
     * to use a license and the keys in that license have expired.
     */
    ERROR_CAS_LICENSE_EXPIRED = 2u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_NO_LICENSE implicitly + 1 */,
    /**
     * The CAS plugin must return ERROR_CAS_SESSION_NOT_OPENED when an
     * attempt is made to use a session that has not been opened.
     */
    ERROR_CAS_SESSION_NOT_OPENED = 3u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_LICENSE_EXPIRED implicitly + 1 */,
    /**
     * The CAS plugin must return ERROR_CAS_CANNOT_HANDLE when an unsupported
     * data format or operation is attempted.
     */
    ERROR_CAS_CANNOT_HANDLE = 4u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_SESSION_NOT_OPENED implicitly + 1 */,
    /**
     * ERROR_CAS_INVALID_STATE must be returned when the device is in a state
     * where it is not able to perform descrambling.
     */
    ERROR_CAS_INVALID_STATE = 5u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_CANNOT_HANDLE implicitly + 1 */,
    /**
     * The CAS plugin must return BAD_VALUE whenever an illegal parameter is
     * passed to one of the interface functions.
     */
    BAD_VALUE = 6u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_INVALID_STATE implicitly + 1 */,
    /**
     * The CAS plugin must return ERROR_CAS_NOT_PROVISIONED when the device
     * has not yet been provisioned.
     */
    ERROR_CAS_NOT_PROVISIONED = 7u /* ::android::hardware::cas::V1_0::Status.BAD_VALUE implicitly + 1 */,
    /**
     * ERROR_CAS_RESOURCE_BUSY must be returned when resources, such as CAS
     * sessions or secure buffers are not available to perform a requested
     * operation because they are already in use.
     */
    ERROR_CAS_RESOURCE_BUSY = 8u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_NOT_PROVISIONED implicitly + 1 */,
    /**
     * The CAS Plugin must return ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION
     * when the output protection level enabled on the device is not
     * sufficient to meet the requirements in the license policy. HDCP is an
     * example of a form of output protection.
     */
    ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION = 9u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_RESOURCE_BUSY implicitly + 1 */,
    /**
     * The CAS Plugin must return ERROR_CAS_TAMPER_DETECTED if an attempt to
     * tamper with the CAS system is detected.
     */
    ERROR_CAS_TAMPER_DETECTED = 10u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION implicitly + 1 */,
    /**
     * The CAS Plugin must return ERROR_CAS_DEVICE_REVOKED if the response
     * indicates that the device has been revoked. Device revocation means
     * that the device is no longer permitted to play content.
     */
    ERROR_CAS_DEVICE_REVOKED = 11u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_TAMPER_DETECTED implicitly + 1 */,
    /**
     * The CAS plugin must return ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED when
     * descrambling is failing because the session is not initialized properly.
     */
    ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED = 12u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_DEVICE_REVOKED implicitly + 1 */,
    /**
     * The CAS Plugin must return ERROR_CAS_DECRYPT if the DescramblerPlugin's
     * descramble operation fails.
     */
    ERROR_CAS_DECRYPT = 13u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED implicitly + 1 */,
    /**
     * ERROR_CAS_UNKNOWN must be returned when a fatal failure occurs and no
     * other defined error is appropriate.
     */
    ERROR_CAS_UNKNOWN = 14u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_DECRYPT implicitly + 1 */,
    /**
     * ERROR_CAS_NEED_ACTIVATION is used to trigger device activation process.
     */
    ERROR_CAS_NEED_ACTIVATION = 15u /* ::android::hardware::cas::V1_0::Status.ERROR_CAS_UNKNOWN implicitly + 1 */,
    /**
     * ERROR_CAS_NEED_PAIRING is used to trigger pairing process.
     */
    ERROR_CAS_NEED_PAIRING = 16u /* ::android::hardware::cas::V1_2::Status.ERROR_CAS_NEED_ACTIVATION implicitly + 1 */,
    /**
     * ERROR_CAS_NO_CARD is used to report no smart card for descrambling.
     */
    ERROR_CAS_NO_CARD = 17u /* ::android::hardware::cas::V1_2::Status.ERROR_CAS_NEED_PAIRING implicitly + 1 */,
    /**
     * ERROR_CAS_CARD_MUTE is used to report smart card is muted for
     * descrambling.
     */
    ERROR_CAS_CARD_MUTE = 18u /* ::android::hardware::cas::V1_2::Status.ERROR_CAS_NO_CARD implicitly + 1 */,
    /**
     *  ERROR_CAS_CARD_INVALID is used to report smart card isn't valid.
     */
    ERROR_CAS_CARD_INVALID = 19u /* ::android::hardware::cas::V1_2::Status.ERROR_CAS_CARD_MUTE implicitly + 1 */,
    /**
     *  ERROR_CAS_BLACKOUT is used to report geographical blackout.
     */
    ERROR_CAS_BLACKOUT = 20u /* ::android::hardware::cas::V1_2::Status.ERROR_CAS_CARD_INVALID implicitly + 1 */,
    /**
     * ERROR_CAS_REBOOTING is used to report CAS is during rebooting.
     */
    ERROR_CAS_REBOOTING = 21u /* ::android::hardware::cas::V1_2::Status.ERROR_CAS_BLACKOUT implicitly + 1 */,
};

/**
 * The intented usage for the session.
 */
enum class SessionIntent : uint32_t {
    /**
     * Live Stream.
     */
    LIVE = 0u,
    /**
     * Playback Recorded Stream.
     */
    PLAYBACK = 1u /* ::android::hardware::cas::V1_2::SessionIntent.LIVE implicitly + 1 */,
    /**
     * Record Live Stream.
     */
    RECORD = 2u /* ::android::hardware::cas::V1_2::SessionIntent.PLAYBACK implicitly + 1 */,
    /**
     * View the content with Time Shift capability
     */
    TIMESHIFT = 3u /* ::android::hardware::cas::V1_2::SessionIntent.RECORD implicitly + 1 */,
};

/**
 * The Scrambling Mode.
 */
enum class ScramblingMode : uint32_t {
    RESERVED = 0u,
    /**
     * DVB (Digital Video Broadcasting) CSA1 (Common Scrambling Algorithm 1) is
     * the default mode and shall be used when the scrambling descriptor
     * is not present in the program map section. DVB scrambling mode is
     * specified in ETSI EN 300 468 specification.
     */
    DVB_CSA1 = 1u /* ::android::hardware::cas::V1_2::ScramblingMode.RESERVED implicitly + 1 */,
    DVB_CSA2 = 2u /* ::android::hardware::cas::V1_2::ScramblingMode.DVB_CSA1 implicitly + 1 */,
    /**
     * DVB-CSA3 in standard mode.
     */
    DVB_CSA3_STANDARD = 3u /* ::android::hardware::cas::V1_2::ScramblingMode.DVB_CSA2 implicitly + 1 */,
    /**
     * DVB-CSA3 in minimally enhanced mode.
     */
    DVB_CSA3_MINIMAL = 4u /* ::android::hardware::cas::V1_2::ScramblingMode.DVB_CSA3_STANDARD implicitly + 1 */,
    /**
     * DVB-CSA3 in fully enhanced mode.
     */
    DVB_CSA3_ENHANCE = 5u /* ::android::hardware::cas::V1_2::ScramblingMode.DVB_CSA3_MINIMAL implicitly + 1 */,
    /**
     * DVB-CISSA version 1.
     */
    DVB_CISSA_V1 = 6u /* ::android::hardware::cas::V1_2::ScramblingMode.DVB_CSA3_ENHANCE implicitly + 1 */,
    /**
     * ATIS-0800006 IIF Default Scrambling Algorithm (IDSA).
     */
    DVB_IDSA = 7u /* ::android::hardware::cas::V1_2::ScramblingMode.DVB_CISSA_V1 implicitly + 1 */,
    /**
     * a symmetric key algorithm.
     */
    MULTI2 = 8u /* ::android::hardware::cas::V1_2::ScramblingMode.DVB_IDSA implicitly + 1 */,
    /**
     * Advanced Encryption System (AES) 128-bit Encryption mode.
     */
    AES128 = 9u /* ::android::hardware::cas::V1_2::ScramblingMode.MULTI2 implicitly + 1 */,
    /**
     * Advanced Encryption System (AES) Electronic Code Book (ECB) mode.
     */
    AES_ECB = 10u /* ::android::hardware::cas::V1_2::ScramblingMode.AES128 implicitly + 1 */,
    /**
     * Advanced Encryption System (AES) Society of Cable Telecommunications
     * Engineers (SCTE) 52 mode.
     */
    AES_SCTE52 = 11u /* ::android::hardware::cas::V1_2::ScramblingMode.AES_ECB implicitly + 1 */,
    /**
     * Triple Data Encryption Algorithm (TDES) Electronic Code Book (ECB) mode.
     */
    TDES_ECB = 12u /* ::android::hardware::cas::V1_2::ScramblingMode.AES_SCTE52 implicitly + 1 */,
    /**
     * Triple Data Encryption Algorithm (TDES) Society of Cable Telecommunications
     * Engineers (SCTE) 52 mode.
     */
    TDES_SCTE52 = 13u /* ::android::hardware::cas::V1_2::ScramblingMode.TDES_ECB implicitly + 1 */,
};

/**
 * The Event Type for status change.
 */
enum class StatusEvent : uint8_t {
    /**
     * The status of CAS plugin was changed due to physical module insertion or
     * removal. Client must call enumeratePlugins to update plugins' status.
     */
    PLUGIN_PHYSICAL_MODULE_CHANGED = 0,
    /**
     * The status of supported session number was changed due to physical module
     * insertion or removal. Client must update session resource according to
     * latest StatusMessage from the StatusEvent. The plugin supports unlimited
     * sesssion by default.
     */
    PLUGIN_SESSION_NUMBER_CHANGED = 1 /* ::android::hardware::cas::V1_2::StatusEvent.PLUGIN_PHYSICAL_MODULE_CHANGED implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::cas::V1_2::Status o);
static inline void PrintTo(::android::hardware::cas::V1_2::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::cas::V1_2::Status lhs, const ::android::hardware::cas::V1_2::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::cas::V1_2::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::cas::V1_2::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::cas::V1_2::Status lhs, const ::android::hardware::cas::V1_2::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::cas::V1_2::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::cas::V1_2::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::cas::V1_2::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::cas::V1_2::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::cas::V1_2::SessionIntent o);
static inline void PrintTo(::android::hardware::cas::V1_2::SessionIntent o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::cas::V1_2::SessionIntent lhs, const ::android::hardware::cas::V1_2::SessionIntent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::cas::V1_2::SessionIntent rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::cas::V1_2::SessionIntent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::cas::V1_2::SessionIntent lhs, const ::android::hardware::cas::V1_2::SessionIntent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::cas::V1_2::SessionIntent rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::cas::V1_2::SessionIntent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::cas::V1_2::SessionIntent e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::cas::V1_2::SessionIntent e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::cas::V1_2::ScramblingMode o);
static inline void PrintTo(::android::hardware::cas::V1_2::ScramblingMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::cas::V1_2::ScramblingMode lhs, const ::android::hardware::cas::V1_2::ScramblingMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::cas::V1_2::ScramblingMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::cas::V1_2::ScramblingMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::cas::V1_2::ScramblingMode lhs, const ::android::hardware::cas::V1_2::ScramblingMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::cas::V1_2::ScramblingMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::cas::V1_2::ScramblingMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::cas::V1_2::ScramblingMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::cas::V1_2::ScramblingMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::cas::V1_2::StatusEvent o);
static inline void PrintTo(::android::hardware::cas::V1_2::StatusEvent o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::cas::V1_2::StatusEvent lhs, const ::android::hardware::cas::V1_2::StatusEvent rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::cas::V1_2::StatusEvent rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::cas::V1_2::StatusEvent lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::cas::V1_2::StatusEvent lhs, const ::android::hardware::cas::V1_2::StatusEvent rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::cas::V1_2::StatusEvent rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::cas::V1_2::StatusEvent lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::cas::V1_2::StatusEvent e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::cas::V1_2::StatusEvent e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::cas::V1_2::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::cas::V1_2::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::cas::V1_2::Status::OK) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::OK;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_NO_LICENSE) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_NO_LICENSE)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_NO_LICENSE";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_NO_LICENSE;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_LICENSE_EXPIRED) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_LICENSE_EXPIRED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_LICENSE_EXPIRED";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_LICENSE_EXPIRED;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_SESSION_NOT_OPENED) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_SESSION_NOT_OPENED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_SESSION_NOT_OPENED";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_SESSION_NOT_OPENED;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_CANNOT_HANDLE) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_CANNOT_HANDLE)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_CANNOT_HANDLE";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_CANNOT_HANDLE;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_INVALID_STATE) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_INVALID_STATE;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::BAD_VALUE) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "BAD_VALUE";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::BAD_VALUE;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_NOT_PROVISIONED) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_NOT_PROVISIONED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_NOT_PROVISIONED";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_NOT_PROVISIONED;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_RESOURCE_BUSY) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_RESOURCE_BUSY)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_RESOURCE_BUSY";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_RESOURCE_BUSY;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_TAMPER_DETECTED) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_TAMPER_DETECTED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_TAMPER_DETECTED";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_TAMPER_DETECTED;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_DEVICE_REVOKED) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_DEVICE_REVOKED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_DEVICE_REVOKED";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_DEVICE_REVOKED;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_DECRYPT) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_DECRYPT)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_DECRYPT";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_DECRYPT;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_UNKNOWN) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_UNKNOWN;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_NEED_ACTIVATION) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_NEED_ACTIVATION)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_NEED_ACTIVATION";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_NEED_ACTIVATION;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_NEED_PAIRING) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_NEED_PAIRING)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_NEED_PAIRING";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_NEED_PAIRING;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_NO_CARD) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_NO_CARD)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_NO_CARD";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_NO_CARD;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_CARD_MUTE) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_CARD_MUTE)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_CARD_MUTE";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_CARD_MUTE;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_CARD_INVALID) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_CARD_INVALID)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_CARD_INVALID";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_CARD_INVALID;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_BLACKOUT) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_BLACKOUT)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_BLACKOUT";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_BLACKOUT;
    }
    if ((o & ::android::hardware::cas::V1_2::Status::ERROR_CAS_REBOOTING) == static_cast<uint32_t>(::android::hardware::cas::V1_2::Status::ERROR_CAS_REBOOTING)) {
        os += (first ? "" : " | ");
        os += "ERROR_CAS_REBOOTING";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::Status::ERROR_CAS_REBOOTING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::cas::V1_2::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::cas::V1_2::Status::OK) {
        return "OK";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_NO_LICENSE) {
        return "ERROR_CAS_NO_LICENSE";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_LICENSE_EXPIRED) {
        return "ERROR_CAS_LICENSE_EXPIRED";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_SESSION_NOT_OPENED) {
        return "ERROR_CAS_SESSION_NOT_OPENED";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_CANNOT_HANDLE) {
        return "ERROR_CAS_CANNOT_HANDLE";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_INVALID_STATE) {
        return "ERROR_CAS_INVALID_STATE";
    }
    if (o == ::android::hardware::cas::V1_2::Status::BAD_VALUE) {
        return "BAD_VALUE";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_NOT_PROVISIONED) {
        return "ERROR_CAS_NOT_PROVISIONED";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_RESOURCE_BUSY) {
        return "ERROR_CAS_RESOURCE_BUSY";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION) {
        return "ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_TAMPER_DETECTED) {
        return "ERROR_CAS_TAMPER_DETECTED";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_DEVICE_REVOKED) {
        return "ERROR_CAS_DEVICE_REVOKED";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED) {
        return "ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_DECRYPT) {
        return "ERROR_CAS_DECRYPT";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_UNKNOWN) {
        return "ERROR_CAS_UNKNOWN";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_NEED_ACTIVATION) {
        return "ERROR_CAS_NEED_ACTIVATION";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_NEED_PAIRING) {
        return "ERROR_CAS_NEED_PAIRING";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_NO_CARD) {
        return "ERROR_CAS_NO_CARD";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_CARD_MUTE) {
        return "ERROR_CAS_CARD_MUTE";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_CARD_INVALID) {
        return "ERROR_CAS_CARD_INVALID";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_BLACKOUT) {
        return "ERROR_CAS_BLACKOUT";
    }
    if (o == ::android::hardware::cas::V1_2::Status::ERROR_CAS_REBOOTING) {
        return "ERROR_CAS_REBOOTING";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::cas::V1_2::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::cas::V1_2::SessionIntent>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::cas::V1_2::SessionIntent> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::cas::V1_2::SessionIntent::LIVE) == static_cast<uint32_t>(::android::hardware::cas::V1_2::SessionIntent::LIVE)) {
        os += (first ? "" : " | ");
        os += "LIVE";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::SessionIntent::LIVE;
    }
    if ((o & ::android::hardware::cas::V1_2::SessionIntent::PLAYBACK) == static_cast<uint32_t>(::android::hardware::cas::V1_2::SessionIntent::PLAYBACK)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::SessionIntent::PLAYBACK;
    }
    if ((o & ::android::hardware::cas::V1_2::SessionIntent::RECORD) == static_cast<uint32_t>(::android::hardware::cas::V1_2::SessionIntent::RECORD)) {
        os += (first ? "" : " | ");
        os += "RECORD";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::SessionIntent::RECORD;
    }
    if ((o & ::android::hardware::cas::V1_2::SessionIntent::TIMESHIFT) == static_cast<uint32_t>(::android::hardware::cas::V1_2::SessionIntent::TIMESHIFT)) {
        os += (first ? "" : " | ");
        os += "TIMESHIFT";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::SessionIntent::TIMESHIFT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::cas::V1_2::SessionIntent o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::cas::V1_2::SessionIntent::LIVE) {
        return "LIVE";
    }
    if (o == ::android::hardware::cas::V1_2::SessionIntent::PLAYBACK) {
        return "PLAYBACK";
    }
    if (o == ::android::hardware::cas::V1_2::SessionIntent::RECORD) {
        return "RECORD";
    }
    if (o == ::android::hardware::cas::V1_2::SessionIntent::TIMESHIFT) {
        return "TIMESHIFT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::cas::V1_2::SessionIntent o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::cas::V1_2::ScramblingMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::cas::V1_2::ScramblingMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::RESERVED) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::RESERVED)) {
        os += (first ? "" : " | ");
        os += "RESERVED";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::RESERVED;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA1) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA1)) {
        os += (first ? "" : " | ");
        os += "DVB_CSA1";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA1;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA2) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA2)) {
        os += (first ? "" : " | ");
        os += "DVB_CSA2";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA2;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_STANDARD) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_STANDARD)) {
        os += (first ? "" : " | ");
        os += "DVB_CSA3_STANDARD";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_STANDARD;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_MINIMAL) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_MINIMAL)) {
        os += (first ? "" : " | ");
        os += "DVB_CSA3_MINIMAL";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_MINIMAL;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_ENHANCE) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_ENHANCE)) {
        os += (first ? "" : " | ");
        os += "DVB_CSA3_ENHANCE";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_ENHANCE;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::DVB_CISSA_V1) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::DVB_CISSA_V1)) {
        os += (first ? "" : " | ");
        os += "DVB_CISSA_V1";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::DVB_CISSA_V1;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::DVB_IDSA) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::DVB_IDSA)) {
        os += (first ? "" : " | ");
        os += "DVB_IDSA";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::DVB_IDSA;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::MULTI2) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::MULTI2)) {
        os += (first ? "" : " | ");
        os += "MULTI2";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::MULTI2;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::AES128) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::AES128)) {
        os += (first ? "" : " | ");
        os += "AES128";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::AES128;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::AES_ECB) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::AES_ECB)) {
        os += (first ? "" : " | ");
        os += "AES_ECB";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::AES_ECB;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::AES_SCTE52) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::AES_SCTE52)) {
        os += (first ? "" : " | ");
        os += "AES_SCTE52";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::AES_SCTE52;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::TDES_ECB) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::TDES_ECB)) {
        os += (first ? "" : " | ");
        os += "TDES_ECB";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::TDES_ECB;
    }
    if ((o & ::android::hardware::cas::V1_2::ScramblingMode::TDES_SCTE52) == static_cast<uint32_t>(::android::hardware::cas::V1_2::ScramblingMode::TDES_SCTE52)) {
        os += (first ? "" : " | ");
        os += "TDES_SCTE52";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::ScramblingMode::TDES_SCTE52;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::cas::V1_2::ScramblingMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::RESERVED) {
        return "RESERVED";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA1) {
        return "DVB_CSA1";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA2) {
        return "DVB_CSA2";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_STANDARD) {
        return "DVB_CSA3_STANDARD";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_MINIMAL) {
        return "DVB_CSA3_MINIMAL";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_ENHANCE) {
        return "DVB_CSA3_ENHANCE";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::DVB_CISSA_V1) {
        return "DVB_CISSA_V1";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::DVB_IDSA) {
        return "DVB_IDSA";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::MULTI2) {
        return "MULTI2";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::AES128) {
        return "AES128";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::AES_ECB) {
        return "AES_ECB";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::AES_SCTE52) {
        return "AES_SCTE52";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::TDES_ECB) {
        return "TDES_ECB";
    }
    if (o == ::android::hardware::cas::V1_2::ScramblingMode::TDES_SCTE52) {
        return "TDES_SCTE52";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::cas::V1_2::ScramblingMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::cas::V1_2::StatusEvent>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::cas::V1_2::StatusEvent> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::cas::V1_2::StatusEvent::PLUGIN_PHYSICAL_MODULE_CHANGED) == static_cast<uint8_t>(::android::hardware::cas::V1_2::StatusEvent::PLUGIN_PHYSICAL_MODULE_CHANGED)) {
        os += (first ? "" : " | ");
        os += "PLUGIN_PHYSICAL_MODULE_CHANGED";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::StatusEvent::PLUGIN_PHYSICAL_MODULE_CHANGED;
    }
    if ((o & ::android::hardware::cas::V1_2::StatusEvent::PLUGIN_SESSION_NUMBER_CHANGED) == static_cast<uint8_t>(::android::hardware::cas::V1_2::StatusEvent::PLUGIN_SESSION_NUMBER_CHANGED)) {
        os += (first ? "" : " | ");
        os += "PLUGIN_SESSION_NUMBER_CHANGED";
        first = false;
        flipped |= ::android::hardware::cas::V1_2::StatusEvent::PLUGIN_SESSION_NUMBER_CHANGED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::cas::V1_2::StatusEvent o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::cas::V1_2::StatusEvent::PLUGIN_PHYSICAL_MODULE_CHANGED) {
        return "PLUGIN_PHYSICAL_MODULE_CHANGED";
    }
    if (o == ::android::hardware::cas::V1_2::StatusEvent::PLUGIN_SESSION_NUMBER_CHANGED) {
        return "PLUGIN_SESSION_NUMBER_CHANGED";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::cas::V1_2::StatusEvent o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_2
}  // namespace cas
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
template<> inline constexpr std::array<::android::hardware::cas::V1_2::Status, 22> hidl_enum_values<::android::hardware::cas::V1_2::Status> = {
    ::android::hardware::cas::V1_2::Status::OK,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_NO_LICENSE,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_LICENSE_EXPIRED,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_SESSION_NOT_OPENED,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_CANNOT_HANDLE,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_INVALID_STATE,
    ::android::hardware::cas::V1_2::Status::BAD_VALUE,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_NOT_PROVISIONED,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_RESOURCE_BUSY,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_INSUFFICIENT_OUTPUT_PROTECTION,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_TAMPER_DETECTED,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_DEVICE_REVOKED,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_DECRYPT,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_UNKNOWN,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_NEED_ACTIVATION,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_NEED_PAIRING,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_NO_CARD,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_CARD_MUTE,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_CARD_INVALID,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_BLACKOUT,
    ::android::hardware::cas::V1_2::Status::ERROR_CAS_REBOOTING,
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
template<> inline constexpr std::array<::android::hardware::cas::V1_2::SessionIntent, 4> hidl_enum_values<::android::hardware::cas::V1_2::SessionIntent> = {
    ::android::hardware::cas::V1_2::SessionIntent::LIVE,
    ::android::hardware::cas::V1_2::SessionIntent::PLAYBACK,
    ::android::hardware::cas::V1_2::SessionIntent::RECORD,
    ::android::hardware::cas::V1_2::SessionIntent::TIMESHIFT,
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
template<> inline constexpr std::array<::android::hardware::cas::V1_2::ScramblingMode, 14> hidl_enum_values<::android::hardware::cas::V1_2::ScramblingMode> = {
    ::android::hardware::cas::V1_2::ScramblingMode::RESERVED,
    ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA1,
    ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA2,
    ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_STANDARD,
    ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_MINIMAL,
    ::android::hardware::cas::V1_2::ScramblingMode::DVB_CSA3_ENHANCE,
    ::android::hardware::cas::V1_2::ScramblingMode::DVB_CISSA_V1,
    ::android::hardware::cas::V1_2::ScramblingMode::DVB_IDSA,
    ::android::hardware::cas::V1_2::ScramblingMode::MULTI2,
    ::android::hardware::cas::V1_2::ScramblingMode::AES128,
    ::android::hardware::cas::V1_2::ScramblingMode::AES_ECB,
    ::android::hardware::cas::V1_2::ScramblingMode::AES_SCTE52,
    ::android::hardware::cas::V1_2::ScramblingMode::TDES_ECB,
    ::android::hardware::cas::V1_2::ScramblingMode::TDES_SCTE52,
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
template<> inline constexpr std::array<::android::hardware::cas::V1_2::StatusEvent, 2> hidl_enum_values<::android::hardware::cas::V1_2::StatusEvent> = {
    ::android::hardware::cas::V1_2::StatusEvent::PLUGIN_PHYSICAL_MODULE_CHANGED,
    ::android::hardware::cas::V1_2::StatusEvent::PLUGIN_SESSION_NUMBER_CHANGED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_2_TYPES_H
