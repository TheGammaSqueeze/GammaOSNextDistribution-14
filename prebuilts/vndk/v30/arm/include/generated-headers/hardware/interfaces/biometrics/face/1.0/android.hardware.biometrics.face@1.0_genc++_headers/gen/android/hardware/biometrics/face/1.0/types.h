#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace face {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class UserHandle : int32_t;
enum class Status : uint32_t;
enum class Feature : uint32_t;
enum class FaceError : int32_t;
enum class FaceAcquiredInfo : int32_t;
struct OptionalUint64;
struct OptionalBool;

/*
 * In the event setActiveUser is not called, all error messages will return
 * this userId.
 */
enum class UserHandle : int32_t {
    NONE = -1 /* -1 */,
};

/**
 * Status codes returned directly by the HIDL method calls upon critical errors
 * where the callback cannot be invoked. Most errors should sent through the
 * onError callback using one of the FaceErrors below.
 */
enum class Status : uint32_t {
    /**
     * The method was invoked successfully.
     */
    OK = 0u,
    /**
     * One of the arguments to the method call is invalid.
     */
    ILLEGAL_ARGUMENT = 1u,
    /**
     * This face HAL does not support this operation.
     */
    OPERATION_NOT_SUPPORTED = 2u,
    /**
     * The HAL has encountered an internal error and cannot complete the request.
     */
    INTERNAL_ERROR = 3u,
    /**
     * The operation could not be completed because there are no enrolled
     * templates.
     */
    NOT_ENROLLED = 4u,
};

enum class Feature : uint32_t {
    /**
     * Require the user to look at the device during enrollment and
     * authentication. Note this is to accommodate people who have limited
     * vision. Must be enabled by default.
     */
    REQUIRE_ATTENTION = 1u,
    /**
     * Require a diverse set of poses during enrollment. Note this is to
     * accommodate people with limited mobility. Must be enabled by default.
     */
    REQUIRE_DIVERSITY = 2u,
};

/**
 * Face errors represent events that can't be immediately recovered by user
 * intervention. These are returned in the onError callback.
 *
 * Upon receiving a face error, clients must terminate the current operation and
 * notify the user where possible.
 */
enum class FaceError : int32_t {
    /**
     * A hardware error has occurred that cannot be resolved. Try again later.
     */
    HW_UNAVAILABLE = 1,
    /**
     * The current enroll or authenticate operation could not be completed,
     * e.g. the sensor was unable to process the current image or the HAT was
     * invalid.
     */
    UNABLE_TO_PROCESS = 2,
    /**
     * The current operation took too long to complete. This is intended to
     * prevent programs from blocking the face HAL indefinitely. The timeout is
     * framework and sensor-specific, but is generally on the order of 30
     * seconds.
     *
     * The timeout is a device-specific time meant to optimize power. For
     * example after 30 seconds of searching for a face it can be use to
     * indicate that the implementation is no longer looking and the framework
     * should restart the operation on the next user interaction.
     */
    TIMEOUT = 3,
    /**
     * The current operation could not be completed because there is not enough
     * storage space remaining to do so.
     */
    NO_SPACE = 4,
    /**
     * The current operation has been cancelled. This may happen if a new
     * request (authenticate, remove, enumerate, enroll) is initiated while
     * an on-going operation is in progress, or if cancel() was called.
     */
    CANCELED = 5,
    /**
     * The current remove operation could not be completed; the face template
     * provided could not be removed.
     */
    UNABLE_TO_REMOVE = 6,
    /**
     * Face authentication is locked out due to too many unsuccessful attempts.
     * This is a "soft" lockout, and authentication can be restarted after
     * a period of time, generally on the order of 30 seconds.
     */
    LOCKOUT = 7,
    /**
     * Used to enable a vendor-specific error message.
     */
    VENDOR = 8,
    /**
     * Face authentication is disabled until the user unlocks with strong
     * authentication (PIN/Pattern/Password).
     */
    LOCKOUT_PERMANENT = 9,
};

/**
 * Face acquisition information provides feedback for the current enrollment
 * or authentication operation.
 *
 * This information indicates that the user can take immediate action to resolve
 * an issue, and clients must ensure that this information is surfaced to the
 * user.
 */
enum class FaceAcquiredInfo : int32_t {
    /**
     * The face acquired was good; no further user interaction is necessary.
     */
    GOOD = 0,
    /**
     * The face data acquired was too noisy or did not have sufficient detail.
     * This is a catch-all for all acquisition errors not captured by the other
     * constants.
     */
    INSUFFICIENT = 1,
    /**
     * Because there was too much ambient light, the captured face data was too
     * bright. It's reasonable to return this after multiple
     * FaceAcquiredInfo.INSUFFICIENT.
     *
     * The user is expected to take action to retry the operation in better
     * lighting conditions when this is returned.
     */
    TOO_BRIGHT = 2,
    /**
     * Because there was not enough illumination, the captured face data was too
     * dark. It's reasonable to return this after multiple
     * FaceAcquiredInfo.INSUFFICIENT.
     *
     * The user is expected to take action to retry the operation in better
     * lighting conditions when this is returned.
     */
    TOO_DARK = 3,
    /**
     * The detected face is too close to the sensor, and the image cannot be
     * processed.
     *
     * The user is expected to be informed to move further from the sensor when
     * this is returned.
     */
    TOO_CLOSE = 4,
    /**
     * The detected face is too small, as the user might be too far away from
     * the sensor.
     *
     * The user is expected to be informed to move closer to the sensor when
     * this is returned.
     */
    TOO_FAR = 5,
    /**
     * Only the upper part of the face was detected. The sensor's field of view
     * is too high.
     *
     * The user should be informed to move up with respect to the sensor when
     * this is returned.
     */
    FACE_TOO_HIGH = 6,
    /**
     * Only the lower part of the face was detected. The sensor's field of view
     * is too low.
     *
     * The user should be informed to move down with respect to the sensor when
     * this is returned.
     */
    FACE_TOO_LOW = 7,
    /**
     * Only the right part of the face was detected. The sensor's field of view
     * is too far right.
     *
     * The user should be informed to move to the right with respect to the
     * sensor when this is returned.
     */
    FACE_TOO_RIGHT = 8,
    /**
     * Only the left part of the face was detected. The sensor's field of view
     * is too far left.
     *
     * The user should be informed to move to the left with respect to the
     * sensor when this is returned.
     */
    FACE_TOO_LEFT = 9,
    /**
     * The user's eyes have strayed away from the sensor. If this message is
     * sent, the user should be informed to look at the device. If the user
     * can't be found in the frame, one of the other acquisition messages
     * must be sent, e.g. NOT_DETECTED.
     */
    POOR_GAZE = 10,
    /**
     * No face was detected within the sensor's field of view.
     *
     * The user should be informed to point the sensor to a face when this is
     * returned.
     */
    NOT_DETECTED = 11,
    /**
     * Too much motion was detected.
     *
     * The user should be informed to keep their face steady relative to the
     * sensor.
     */
    TOO_MUCH_MOTION = 12,
    /**
     * The sensor needs to be re-calibrated. This is an unexpected condition,
     * and must only be sent if a serious, uncorrectable, and unrecoverable
     * calibration issue is detected which requires user intervention, e.g.
     * re-enrolling. The expected response to this message is to direct the
     * user to re-enroll.
     */
    RECALIBRATE = 13,
    /**
     * The face is too different from a previous acquisition. This condition
     * only applies to enrollment. This can happen if the user passes the
     * device to someone else in the middle of enrollment.
     */
    TOO_DIFFERENT = 14,
    /**
     * The face is too similar to a previous acquisition. This condition only
     * applies to enrollment. The user should change their pose.
     */
    TOO_SIMILAR = 15,
    /**
     * The magnitude of the pan angle of the user’s face with respect to the sensor’s
     * capture plane is too high.
     *
     * The pan angle is defined as the angle swept out by the user’s face turning
     * their neck left and right. The pan angle would be zero if the user faced the
     * camera directly.
     *
     * The user should be informed to look more directly at the camera.
     */
    PAN_TOO_EXTREME = 16,
    /**
     * The magnitude of the tilt angle of the user’s face with respect to the sensor’s
     * capture plane is too high.
     *
     * The tilt angle is defined as the angle swept out by the user’s face looking up
     * and down. The tilt angle would be zero if the user faced the camera directly.
     *
     * The user should be informed to look more directly at the camera.
     */
    TILT_TOO_EXTREME = 17,
    /**
     * The magnitude of the roll angle of the user’s face with respect to the sensor’s
     * capture plane is too high.
     *
     * The roll angle is defined as the angle swept out by the user’s face tilting their head
     * towards their shoulders to the left and right. The roll angle would be zero if the user's
     * head is vertically aligned with the camera.
     *
     * The user should be informed to look more directly at the camera.
     */
    ROLL_TOO_EXTREME = 18,
    /**
     * The user’s face has been obscured by some object.
     *
     * The user should be informed to remove any objects from the line of sight from
     * the sensor to the user’s face.
     */
    FACE_OBSCURED = 19,
    /**
     * This message represents the earliest message sent at the beginning of the authentication
     * pipeline. It is expected to be used to measure latency. For example, in a camera-based
     * authentication system it's expected to be sent prior to camera initialization. Note this
     * should be sent whenever authentication is restarted (see IBiometricsFace#userActivity).
     * The framework will measure latency based on the time between the last START message and the
     * onAuthenticated callback.
     */
    START = 20,
    /**
     * The sensor is dirty. The user should be informed to clean the sensor.
     */
    SENSOR_DIRTY = 21,
    /**
     * Used to enable a vendor-specific acquisition message.
     */
    VENDOR = 22,
};

/**
 * Result structure with an additional uint64_t field. See documentation in
 * setCallback(), preEnroll(), and getAuthenticatorId() for usage of the value.
 */
struct OptionalUint64 final {
    /**
     * The return status.
     */
    ::android::hardware::biometrics::face::V1_0::Status status __attribute__ ((aligned(4)));
    /**
     * This value is only meaningful if status is OK.
     */
    uint64_t value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::biometrics::face::V1_0::OptionalUint64, status) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::biometrics::face::V1_0::OptionalUint64, value) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::biometrics::face::V1_0::OptionalUint64) == 16, "wrong size");
static_assert(__alignof(::android::hardware::biometrics::face::V1_0::OptionalUint64) == 8, "wrong alignment");

/**
 * Result structure with an addition bool field. See documentation in
 * getFeature() for usage of the value.
 */
struct OptionalBool final {
    /**
     * The return status.
     */
    ::android::hardware::biometrics::face::V1_0::Status status __attribute__ ((aligned(4)));
    /**
     * This value is only meaningful if status is OK.
     */
    bool value __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::biometrics::face::V1_0::OptionalBool, status) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::biometrics::face::V1_0::OptionalBool, value) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::biometrics::face::V1_0::OptionalBool) == 8, "wrong size");
static_assert(__alignof(::android::hardware::biometrics::face::V1_0::OptionalBool) == 4, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::biometrics::face::V1_0::UserHandle o);
static inline void PrintTo(::android::hardware::biometrics::face::V1_0::UserHandle o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::biometrics::face::V1_0::UserHandle lhs, const ::android::hardware::biometrics::face::V1_0::UserHandle rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::biometrics::face::V1_0::UserHandle rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::biometrics::face::V1_0::UserHandle lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::biometrics::face::V1_0::UserHandle lhs, const ::android::hardware::biometrics::face::V1_0::UserHandle rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::biometrics::face::V1_0::UserHandle rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::biometrics::face::V1_0::UserHandle lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::biometrics::face::V1_0::UserHandle e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::biometrics::face::V1_0::UserHandle e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::biometrics::face::V1_0::Status o);
static inline void PrintTo(::android::hardware::biometrics::face::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::biometrics::face::V1_0::Status lhs, const ::android::hardware::biometrics::face::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::biometrics::face::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::biometrics::face::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::biometrics::face::V1_0::Status lhs, const ::android::hardware::biometrics::face::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::biometrics::face::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::biometrics::face::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::biometrics::face::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::biometrics::face::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::biometrics::face::V1_0::Feature o);
static inline void PrintTo(::android::hardware::biometrics::face::V1_0::Feature o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::biometrics::face::V1_0::Feature lhs, const ::android::hardware::biometrics::face::V1_0::Feature rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::biometrics::face::V1_0::Feature rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::biometrics::face::V1_0::Feature lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::biometrics::face::V1_0::Feature lhs, const ::android::hardware::biometrics::face::V1_0::Feature rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::biometrics::face::V1_0::Feature rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::biometrics::face::V1_0::Feature lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::biometrics::face::V1_0::Feature e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::biometrics::face::V1_0::Feature e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::biometrics::face::V1_0::FaceError o);
static inline void PrintTo(::android::hardware::biometrics::face::V1_0::FaceError o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::biometrics::face::V1_0::FaceError lhs, const ::android::hardware::biometrics::face::V1_0::FaceError rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::biometrics::face::V1_0::FaceError rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::biometrics::face::V1_0::FaceError lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::biometrics::face::V1_0::FaceError lhs, const ::android::hardware::biometrics::face::V1_0::FaceError rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::biometrics::face::V1_0::FaceError rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::biometrics::face::V1_0::FaceError lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::biometrics::face::V1_0::FaceError e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::biometrics::face::V1_0::FaceError e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo o);
static inline void PrintTo(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo lhs, const ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo lhs, const ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& o);
static inline void PrintTo(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& lhs, const ::android::hardware::biometrics::face::V1_0::OptionalUint64& rhs);
static inline bool operator!=(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& lhs, const ::android::hardware::biometrics::face::V1_0::OptionalUint64& rhs);

static inline std::string toString(const ::android::hardware::biometrics::face::V1_0::OptionalBool& o);
static inline void PrintTo(const ::android::hardware::biometrics::face::V1_0::OptionalBool& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::biometrics::face::V1_0::OptionalBool& lhs, const ::android::hardware::biometrics::face::V1_0::OptionalBool& rhs);
static inline bool operator!=(const ::android::hardware::biometrics::face::V1_0::OptionalBool& lhs, const ::android::hardware::biometrics::face::V1_0::OptionalBool& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::biometrics::face::V1_0::UserHandle>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::biometrics::face::V1_0::UserHandle> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::biometrics::face::V1_0::UserHandle::NONE) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::UserHandle::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::UserHandle::NONE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::biometrics::face::V1_0::UserHandle o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::biometrics::face::V1_0::UserHandle::NONE) {
        return "NONE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::biometrics::face::V1_0::UserHandle o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::biometrics::face::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::biometrics::face::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::biometrics::face::V1_0::Status::OK) == static_cast<uint32_t>(::android::hardware::biometrics::face::V1_0::Status::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::Status::OK;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::Status::ILLEGAL_ARGUMENT) == static_cast<uint32_t>(::android::hardware::biometrics::face::V1_0::Status::ILLEGAL_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "ILLEGAL_ARGUMENT";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::Status::ILLEGAL_ARGUMENT;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::Status::OPERATION_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::biometrics::face::V1_0::Status::OPERATION_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "OPERATION_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::Status::OPERATION_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::Status::INTERNAL_ERROR) == static_cast<uint32_t>(::android::hardware::biometrics::face::V1_0::Status::INTERNAL_ERROR)) {
        os += (first ? "" : " | ");
        os += "INTERNAL_ERROR";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::Status::INTERNAL_ERROR;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::Status::NOT_ENROLLED) == static_cast<uint32_t>(::android::hardware::biometrics::face::V1_0::Status::NOT_ENROLLED)) {
        os += (first ? "" : " | ");
        os += "NOT_ENROLLED";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::Status::NOT_ENROLLED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::biometrics::face::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::biometrics::face::V1_0::Status::OK) {
        return "OK";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::Status::ILLEGAL_ARGUMENT) {
        return "ILLEGAL_ARGUMENT";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::Status::OPERATION_NOT_SUPPORTED) {
        return "OPERATION_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::Status::INTERNAL_ERROR) {
        return "INTERNAL_ERROR";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::Status::NOT_ENROLLED) {
        return "NOT_ENROLLED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::biometrics::face::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::biometrics::face::V1_0::Feature>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::biometrics::face::V1_0::Feature> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::biometrics::face::V1_0::Feature::REQUIRE_ATTENTION) == static_cast<uint32_t>(::android::hardware::biometrics::face::V1_0::Feature::REQUIRE_ATTENTION)) {
        os += (first ? "" : " | ");
        os += "REQUIRE_ATTENTION";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::Feature::REQUIRE_ATTENTION;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::Feature::REQUIRE_DIVERSITY) == static_cast<uint32_t>(::android::hardware::biometrics::face::V1_0::Feature::REQUIRE_DIVERSITY)) {
        os += (first ? "" : " | ");
        os += "REQUIRE_DIVERSITY";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::Feature::REQUIRE_DIVERSITY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::biometrics::face::V1_0::Feature o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::biometrics::face::V1_0::Feature::REQUIRE_ATTENTION) {
        return "REQUIRE_ATTENTION";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::Feature::REQUIRE_DIVERSITY) {
        return "REQUIRE_DIVERSITY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::biometrics::face::V1_0::Feature o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::biometrics::face::V1_0::FaceError>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::biometrics::face::V1_0::FaceError> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceError::HW_UNAVAILABLE) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceError::HW_UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "HW_UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceError::HW_UNAVAILABLE;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceError::UNABLE_TO_PROCESS) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceError::UNABLE_TO_PROCESS)) {
        os += (first ? "" : " | ");
        os += "UNABLE_TO_PROCESS";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceError::UNABLE_TO_PROCESS;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceError::TIMEOUT) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceError::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceError::TIMEOUT;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceError::NO_SPACE) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceError::NO_SPACE)) {
        os += (first ? "" : " | ");
        os += "NO_SPACE";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceError::NO_SPACE;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceError::CANCELED) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceError::CANCELED)) {
        os += (first ? "" : " | ");
        os += "CANCELED";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceError::CANCELED;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceError::UNABLE_TO_REMOVE) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceError::UNABLE_TO_REMOVE)) {
        os += (first ? "" : " | ");
        os += "UNABLE_TO_REMOVE";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceError::UNABLE_TO_REMOVE;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceError::LOCKOUT) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceError::LOCKOUT)) {
        os += (first ? "" : " | ");
        os += "LOCKOUT";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceError::LOCKOUT;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceError::VENDOR) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceError::VENDOR)) {
        os += (first ? "" : " | ");
        os += "VENDOR";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceError::VENDOR;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceError::LOCKOUT_PERMANENT) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceError::LOCKOUT_PERMANENT)) {
        os += (first ? "" : " | ");
        os += "LOCKOUT_PERMANENT";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceError::LOCKOUT_PERMANENT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::biometrics::face::V1_0::FaceError o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::biometrics::face::V1_0::FaceError::HW_UNAVAILABLE) {
        return "HW_UNAVAILABLE";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceError::UNABLE_TO_PROCESS) {
        return "UNABLE_TO_PROCESS";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceError::TIMEOUT) {
        return "TIMEOUT";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceError::NO_SPACE) {
        return "NO_SPACE";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceError::CANCELED) {
        return "CANCELED";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceError::UNABLE_TO_REMOVE) {
        return "UNABLE_TO_REMOVE";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceError::LOCKOUT) {
        return "LOCKOUT";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceError::VENDOR) {
        return "VENDOR";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceError::LOCKOUT_PERMANENT) {
        return "LOCKOUT_PERMANENT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::biometrics::face::V1_0::FaceError o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::GOOD) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::GOOD)) {
        os += (first ? "" : " | ");
        os += "GOOD";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::GOOD;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::INSUFFICIENT) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::INSUFFICIENT)) {
        os += (first ? "" : " | ");
        os += "INSUFFICIENT";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::INSUFFICIENT;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_BRIGHT) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_BRIGHT)) {
        os += (first ? "" : " | ");
        os += "TOO_BRIGHT";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_BRIGHT;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_DARK) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_DARK)) {
        os += (first ? "" : " | ");
        os += "TOO_DARK";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_DARK;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_CLOSE) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_CLOSE)) {
        os += (first ? "" : " | ");
        os += "TOO_CLOSE";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_CLOSE;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_FAR) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_FAR)) {
        os += (first ? "" : " | ");
        os += "TOO_FAR";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_FAR;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_HIGH) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_HIGH)) {
        os += (first ? "" : " | ");
        os += "FACE_TOO_HIGH";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_HIGH;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_LOW) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_LOW)) {
        os += (first ? "" : " | ");
        os += "FACE_TOO_LOW";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_LOW;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_RIGHT) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_RIGHT)) {
        os += (first ? "" : " | ");
        os += "FACE_TOO_RIGHT";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_RIGHT;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_LEFT) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_LEFT)) {
        os += (first ? "" : " | ");
        os += "FACE_TOO_LEFT";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_LEFT;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::POOR_GAZE) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::POOR_GAZE)) {
        os += (first ? "" : " | ");
        os += "POOR_GAZE";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::POOR_GAZE;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::NOT_DETECTED) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::NOT_DETECTED)) {
        os += (first ? "" : " | ");
        os += "NOT_DETECTED";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::NOT_DETECTED;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_MUCH_MOTION) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_MUCH_MOTION)) {
        os += (first ? "" : " | ");
        os += "TOO_MUCH_MOTION";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_MUCH_MOTION;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::RECALIBRATE) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::RECALIBRATE)) {
        os += (first ? "" : " | ");
        os += "RECALIBRATE";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::RECALIBRATE;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_DIFFERENT) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_DIFFERENT)) {
        os += (first ? "" : " | ");
        os += "TOO_DIFFERENT";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_DIFFERENT;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_SIMILAR) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_SIMILAR)) {
        os += (first ? "" : " | ");
        os += "TOO_SIMILAR";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_SIMILAR;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::PAN_TOO_EXTREME) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::PAN_TOO_EXTREME)) {
        os += (first ? "" : " | ");
        os += "PAN_TOO_EXTREME";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::PAN_TOO_EXTREME;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TILT_TOO_EXTREME) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TILT_TOO_EXTREME)) {
        os += (first ? "" : " | ");
        os += "TILT_TOO_EXTREME";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TILT_TOO_EXTREME;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::ROLL_TOO_EXTREME) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::ROLL_TOO_EXTREME)) {
        os += (first ? "" : " | ");
        os += "ROLL_TOO_EXTREME";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::ROLL_TOO_EXTREME;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_OBSCURED) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_OBSCURED)) {
        os += (first ? "" : " | ");
        os += "FACE_OBSCURED";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_OBSCURED;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::START) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::START)) {
        os += (first ? "" : " | ");
        os += "START";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::START;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::SENSOR_DIRTY) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::SENSOR_DIRTY)) {
        os += (first ? "" : " | ");
        os += "SENSOR_DIRTY";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::SENSOR_DIRTY;
    }
    if ((o & ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::VENDOR) == static_cast<int32_t>(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::VENDOR)) {
        os += (first ? "" : " | ");
        os += "VENDOR";
        first = false;
        flipped |= ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::VENDOR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::GOOD) {
        return "GOOD";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::INSUFFICIENT) {
        return "INSUFFICIENT";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_BRIGHT) {
        return "TOO_BRIGHT";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_DARK) {
        return "TOO_DARK";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_CLOSE) {
        return "TOO_CLOSE";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_FAR) {
        return "TOO_FAR";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_HIGH) {
        return "FACE_TOO_HIGH";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_LOW) {
        return "FACE_TOO_LOW";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_RIGHT) {
        return "FACE_TOO_RIGHT";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_LEFT) {
        return "FACE_TOO_LEFT";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::POOR_GAZE) {
        return "POOR_GAZE";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::NOT_DETECTED) {
        return "NOT_DETECTED";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_MUCH_MOTION) {
        return "TOO_MUCH_MOTION";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::RECALIBRATE) {
        return "RECALIBRATE";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_DIFFERENT) {
        return "TOO_DIFFERENT";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_SIMILAR) {
        return "TOO_SIMILAR";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::PAN_TOO_EXTREME) {
        return "PAN_TOO_EXTREME";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TILT_TOO_EXTREME) {
        return "TILT_TOO_EXTREME";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::ROLL_TOO_EXTREME) {
        return "ROLL_TOO_EXTREME";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_OBSCURED) {
        return "FACE_OBSCURED";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::START) {
        return "START";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::SENSOR_DIRTY) {
        return "SENSOR_DIRTY";
    }
    if (o == ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::VENDOR) {
        return "VENDOR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status = ";
    os += ::android::hardware::biometrics::face::V1_0::toString(o.status);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& lhs, const ::android::hardware::biometrics::face::V1_0::OptionalUint64& rhs) {
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::biometrics::face::V1_0::OptionalUint64& lhs, const ::android::hardware::biometrics::face::V1_0::OptionalUint64& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::biometrics::face::V1_0::OptionalBool& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status = ";
    os += ::android::hardware::biometrics::face::V1_0::toString(o.status);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::biometrics::face::V1_0::OptionalBool& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::biometrics::face::V1_0::OptionalBool& lhs, const ::android::hardware::biometrics::face::V1_0::OptionalBool& rhs) {
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::biometrics::face::V1_0::OptionalBool& lhs, const ::android::hardware::biometrics::face::V1_0::OptionalBool& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace face
}  // namespace biometrics
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
template<> inline constexpr std::array<::android::hardware::biometrics::face::V1_0::UserHandle, 1> hidl_enum_values<::android::hardware::biometrics::face::V1_0::UserHandle> = {
    ::android::hardware::biometrics::face::V1_0::UserHandle::NONE,
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
template<> inline constexpr std::array<::android::hardware::biometrics::face::V1_0::Status, 5> hidl_enum_values<::android::hardware::biometrics::face::V1_0::Status> = {
    ::android::hardware::biometrics::face::V1_0::Status::OK,
    ::android::hardware::biometrics::face::V1_0::Status::ILLEGAL_ARGUMENT,
    ::android::hardware::biometrics::face::V1_0::Status::OPERATION_NOT_SUPPORTED,
    ::android::hardware::biometrics::face::V1_0::Status::INTERNAL_ERROR,
    ::android::hardware::biometrics::face::V1_0::Status::NOT_ENROLLED,
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
template<> inline constexpr std::array<::android::hardware::biometrics::face::V1_0::Feature, 2> hidl_enum_values<::android::hardware::biometrics::face::V1_0::Feature> = {
    ::android::hardware::biometrics::face::V1_0::Feature::REQUIRE_ATTENTION,
    ::android::hardware::biometrics::face::V1_0::Feature::REQUIRE_DIVERSITY,
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
template<> inline constexpr std::array<::android::hardware::biometrics::face::V1_0::FaceError, 9> hidl_enum_values<::android::hardware::biometrics::face::V1_0::FaceError> = {
    ::android::hardware::biometrics::face::V1_0::FaceError::HW_UNAVAILABLE,
    ::android::hardware::biometrics::face::V1_0::FaceError::UNABLE_TO_PROCESS,
    ::android::hardware::biometrics::face::V1_0::FaceError::TIMEOUT,
    ::android::hardware::biometrics::face::V1_0::FaceError::NO_SPACE,
    ::android::hardware::biometrics::face::V1_0::FaceError::CANCELED,
    ::android::hardware::biometrics::face::V1_0::FaceError::UNABLE_TO_REMOVE,
    ::android::hardware::biometrics::face::V1_0::FaceError::LOCKOUT,
    ::android::hardware::biometrics::face::V1_0::FaceError::VENDOR,
    ::android::hardware::biometrics::face::V1_0::FaceError::LOCKOUT_PERMANENT,
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
template<> inline constexpr std::array<::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo, 23> hidl_enum_values<::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo> = {
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::GOOD,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::INSUFFICIENT,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_BRIGHT,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_DARK,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_CLOSE,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_FAR,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_HIGH,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_LOW,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_RIGHT,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_TOO_LEFT,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::POOR_GAZE,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::NOT_DETECTED,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_MUCH_MOTION,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::RECALIBRATE,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_DIFFERENT,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TOO_SIMILAR,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::PAN_TOO_EXTREME,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::TILT_TOO_EXTREME,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::ROLL_TOO_EXTREME,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::FACE_OBSCURED,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::START,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::SENSOR_DIRTY,
    ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo::VENDOR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_TYPES_H
