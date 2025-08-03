#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_1_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace fingerprint {
namespace V2_1 {

// Forward declaration for forward reference support:
enum class RequestStatus : int32_t;
enum class FingerprintError : int32_t;
enum class FingerprintAcquiredInfo : int32_t;
struct FingerprintFingerId;
struct FingerprintEnroll;
struct FingerprintIterator;
struct FingerprintAcquired;
struct FingerprintAuthenticated;
enum class FingerprintMsgType : int32_t;

/**
 * Request status indicates whether the request is accepted by the vendor
 * implementation or not. These values are taken from the errno set,
 * except for the SYS_UNKNOWN
 */
enum class RequestStatus : int32_t {
    SYS_UNKNOWN = 1,
    SYS_OK = 0,
    SYS_ENOENT = -2 /* -2 */,
    SYS_EINTR = -4 /* -4 */,
    SYS_EIO = -5 /* -5 */,
    SYS_EAGAIN = -11 /* -11 */,
    SYS_ENOMEM = -12 /* -12 */,
    SYS_EACCES = -13 /* -13 */,
    SYS_EFAULT = -14 /* -14 */,
    SYS_EBUSY = -16 /* -16 */,
    SYS_EINVAL = -22 /* -22 */,
    SYS_ENOSPC = -28 /* -28 */,
    SYS_ETIMEDOUT = -110 /* -110 */,
};

/**
 * Fingerprint errors are meant to tell the framework to terminate the current
 * operation and ask for the user to correct the situation. These will almost
 * always result in messaging and user interaction to correct the problem.
 *
 * For example, ERROR_CANCELED should follow any acquisition message that
 * results in a situation where the current operation can't continue without
 * user interaction. For example, if the sensor is dirty during enrollment and
 * no further enrollment progress can be made, send ACQUIRED_IMAGER_DIRTY
 * followed by ERROR_CANCELED.
 */
enum class FingerprintError : int32_t {
    /**
     * Used for testing, no error returned
     */
    ERROR_NO_ERROR = 0,
    /**
     * The hardware has an error that can't be resolved.
     */
    ERROR_HW_UNAVAILABLE = 1,
    /**
     * Bad data; operation can't continue
     */
    ERROR_UNABLE_TO_PROCESS = 2,
    /**
     * The operation has timed out waiting for user input.
     */
    ERROR_TIMEOUT = 3,
    /**
     * No space available to store a template
     */
    ERROR_NO_SPACE = 4,
    /**
     * The current operation has been canceled
     */
    ERROR_CANCELED = 5,
    /**
     * Unable to remove a template
     */
    ERROR_UNABLE_TO_REMOVE = 6,
    /**
     * The hardware is in lockout due to too many attempts
     */
    ERROR_LOCKOUT = 7,
    /**
     * Vendor-specific error message
     */
    ERROR_VENDOR = 8,
};

/**
 * Fingerprint acquisition info is meant as feedback for the current operation.
 * Anything but ACQUIRED_GOOD must be shown to the user as feedback on how to
 * take action on the current operation. For example, ACQUIRED_IMAGER_DIRTY may
 * be used to tell the user to clean the sensor if it is detected to be dirty.
 * If this causes the current operation to fail, an additional ERROR_CANCELED
 * must be sent to stop the operation in progress (e.g. enrollment).
 * In general, these messages will result in a "Try again" message.
 */
enum class FingerprintAcquiredInfo : int32_t {
    ACQUIRED_GOOD = 0,
    /**
     * sensor needs more data, i.e. longer swipe.
     */
    ACQUIRED_PARTIAL = 1,
    /**
     * image doesn't contain enough detail for recognition
     */
    ACQUIRED_INSUFFICIENT = 2,
    /**
     * sensor needs to be cleaned
     */
    ACQUIRED_IMAGER_DIRTY = 3,
    /**
     * mostly swipe-type sensors; not enough data collected
     */
    ACQUIRED_TOO_SLOW = 4,
    /**
     * vendor-specific acquisition messages start here
     */
    ACQUIRED_TOO_FAST = 5,
    /**
     * vendor-specific acquisition messages
     */
    ACQUIRED_VENDOR = 6,
};

struct FingerprintFingerId final {
    /**
     * Group ID
     */
    uint32_t gid __attribute__ ((aligned(4)));
    /**
     * Fingerprint template ID
     */
    uint32_t fid __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId, gid) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId, fid) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId) == 8, "wrong size");
static_assert(__alignof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId) == 4, "wrong alignment");

struct FingerprintEnroll final {
    /**
     * Template ID
     */
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId finger __attribute__ ((aligned(4)));
    /**
     * samplesRemaining goes from N (no data collected, but N scans needed)
     * to 0 (no more data is needed to build a template).
     */
    uint32_t samplesRemaining __attribute__ ((aligned(4)));
    /**
     * Vendor specific message. Used for user guidance
     */
    uint64_t msg __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll, finger) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll, samplesRemaining) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll, msg) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll) == 24, "wrong size");
static_assert(__alignof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll) == 8, "wrong alignment");

struct FingerprintIterator final {
    /**
     * Template ID
     */
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId finger __attribute__ ((aligned(4)));
    /**
     * How many templates remain to iterate through
     */
    uint32_t remainingTemplates __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator, finger) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator, remainingTemplates) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator) == 12, "wrong size");
static_assert(__alignof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator) == 4, "wrong alignment");

typedef ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator FingerprintEnumerated;

typedef ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator FingerprintRemoved;

struct FingerprintAcquired final {
    /**
     * information about the image
     */
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo acquiredInfo __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired, acquiredInfo) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired) == 4, "wrong size");
static_assert(__alignof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired) == 4, "wrong alignment");

struct FingerprintAuthenticated final {
    /**
     * Matched template ID
     */
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId finger __attribute__ ((aligned(4)));
    /**
     * Authentication result from the keymaster
     */
    ::android::hardware::hidl_array<uint8_t, 69> hat __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated, finger) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated, hat) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated) == 80, "wrong size");
static_assert(__alignof(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated) == 4, "wrong alignment");

/**
 * Run time type identification for the notify() call payload.
 */
enum class FingerprintMsgType : int32_t {
    ERROR = -1 /* -1 */,
    ACQUIRED = 1,
    TEMPLATE_ENROLLING = 3,
    TEMPLATE_REMOVED = 4,
    AUTHENTICATED = 5,
    TEMPLATE_ENUMERATING = 6,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus o);
static inline void PrintTo(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus lhs, const ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus lhs, const ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError o);
static inline void PrintTo(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo o);
static inline void PrintTo(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& o);
static inline void PrintTo(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& rhs);
static inline bool operator!=(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& rhs);

static inline std::string toString(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& o);
static inline void PrintTo(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& rhs);
static inline bool operator!=(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& rhs);

static inline std::string toString(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& o);
static inline void PrintTo(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& rhs);
static inline bool operator!=(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& rhs);

static inline std::string toString(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& o);
static inline void PrintTo(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& rhs);
static inline bool operator!=(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& rhs);

static inline std::string toString(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& o);
static inline void PrintTo(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& rhs);
static inline bool operator!=(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType o);
static inline void PrintTo(::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_UNKNOWN) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "SYS_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_UNKNOWN;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_OK) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_OK)) {
        os += (first ? "" : " | ");
        os += "SYS_OK";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_OK;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOENT) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOENT)) {
        os += (first ? "" : " | ");
        os += "SYS_ENOENT";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOENT;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EINTR) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EINTR)) {
        os += (first ? "" : " | ");
        os += "SYS_EINTR";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EINTR;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EIO) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EIO)) {
        os += (first ? "" : " | ");
        os += "SYS_EIO";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EIO;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EAGAIN) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EAGAIN)) {
        os += (first ? "" : " | ");
        os += "SYS_EAGAIN";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EAGAIN;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOMEM) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOMEM)) {
        os += (first ? "" : " | ");
        os += "SYS_ENOMEM";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOMEM;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EACCES) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EACCES)) {
        os += (first ? "" : " | ");
        os += "SYS_EACCES";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EACCES;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EFAULT) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EFAULT)) {
        os += (first ? "" : " | ");
        os += "SYS_EFAULT";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EFAULT;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EBUSY) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EBUSY)) {
        os += (first ? "" : " | ");
        os += "SYS_EBUSY";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EBUSY;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EINVAL) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EINVAL)) {
        os += (first ? "" : " | ");
        os += "SYS_EINVAL";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EINVAL;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOSPC) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOSPC)) {
        os += (first ? "" : " | ");
        os += "SYS_ENOSPC";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOSPC;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ETIMEDOUT) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ETIMEDOUT)) {
        os += (first ? "" : " | ");
        os += "SYS_ETIMEDOUT";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ETIMEDOUT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_UNKNOWN) {
        return "SYS_UNKNOWN";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_OK) {
        return "SYS_OK";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOENT) {
        return "SYS_ENOENT";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EINTR) {
        return "SYS_EINTR";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EIO) {
        return "SYS_EIO";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EAGAIN) {
        return "SYS_EAGAIN";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOMEM) {
        return "SYS_ENOMEM";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EACCES) {
        return "SYS_EACCES";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EFAULT) {
        return "SYS_EFAULT";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EBUSY) {
        return "SYS_EBUSY";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EINVAL) {
        return "SYS_EINVAL";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOSPC) {
        return "SYS_ENOSPC";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ETIMEDOUT) {
        return "SYS_ETIMEDOUT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::biometrics::fingerprint::V2_1::RequestStatus o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::biometrics::fingerprint::V2_1::FingerprintError>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::biometrics::fingerprint::V2_1::FingerprintError> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_NO_ERROR) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_NO_ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR_NO_ERROR";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_NO_ERROR;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_HW_UNAVAILABLE) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_HW_UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "ERROR_HW_UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_HW_UNAVAILABLE;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_UNABLE_TO_PROCESS) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_UNABLE_TO_PROCESS)) {
        os += (first ? "" : " | ");
        os += "ERROR_UNABLE_TO_PROCESS";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_UNABLE_TO_PROCESS;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_TIMEOUT) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "ERROR_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_TIMEOUT;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_NO_SPACE) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_NO_SPACE)) {
        os += (first ? "" : " | ");
        os += "ERROR_NO_SPACE";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_NO_SPACE;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_CANCELED) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_CANCELED)) {
        os += (first ? "" : " | ");
        os += "ERROR_CANCELED";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_CANCELED;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_UNABLE_TO_REMOVE) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_UNABLE_TO_REMOVE)) {
        os += (first ? "" : " | ");
        os += "ERROR_UNABLE_TO_REMOVE";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_UNABLE_TO_REMOVE;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_LOCKOUT) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_LOCKOUT)) {
        os += (first ? "" : " | ");
        os += "ERROR_LOCKOUT";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_LOCKOUT;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_VENDOR) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_VENDOR)) {
        os += (first ? "" : " | ");
        os += "ERROR_VENDOR";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_VENDOR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_NO_ERROR) {
        return "ERROR_NO_ERROR";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_HW_UNAVAILABLE) {
        return "ERROR_HW_UNAVAILABLE";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_UNABLE_TO_PROCESS) {
        return "ERROR_UNABLE_TO_PROCESS";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_TIMEOUT) {
        return "ERROR_TIMEOUT";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_NO_SPACE) {
        return "ERROR_NO_SPACE";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_CANCELED) {
        return "ERROR_CANCELED";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_UNABLE_TO_REMOVE) {
        return "ERROR_UNABLE_TO_REMOVE";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_LOCKOUT) {
        return "ERROR_LOCKOUT";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_VENDOR) {
        return "ERROR_VENDOR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::biometrics::fingerprint::V2_1::FingerprintError o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_GOOD) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_GOOD)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_GOOD";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_GOOD;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_PARTIAL) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_PARTIAL)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_PARTIAL";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_PARTIAL;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_INSUFFICIENT) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_INSUFFICIENT)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_INSUFFICIENT";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_INSUFFICIENT;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_IMAGER_DIRTY) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_IMAGER_DIRTY)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_IMAGER_DIRTY";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_IMAGER_DIRTY;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_TOO_SLOW) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_TOO_SLOW)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_TOO_SLOW";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_TOO_SLOW;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_TOO_FAST) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_TOO_FAST)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_TOO_FAST";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_TOO_FAST;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_VENDOR) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_VENDOR)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED_VENDOR";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_VENDOR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_GOOD) {
        return "ACQUIRED_GOOD";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_PARTIAL) {
        return "ACQUIRED_PARTIAL";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_INSUFFICIENT) {
        return "ACQUIRED_INSUFFICIENT";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_IMAGER_DIRTY) {
        return "ACQUIRED_IMAGER_DIRTY";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_TOO_SLOW) {
        return "ACQUIRED_TOO_SLOW";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_TOO_FAST) {
        return "ACQUIRED_TOO_FAST";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_VENDOR) {
        return "ACQUIRED_VENDOR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".gid = ";
    os += ::android::hardware::toString(o.gid);
    os += ", .fid = ";
    os += ::android::hardware::toString(o.fid);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& rhs) {
    if (lhs.gid != rhs.gid) {
        return false;
    }
    if (lhs.fid != rhs.fid) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintFingerId& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".finger = ";
    os += ::android::hardware::biometrics::fingerprint::V2_1::toString(o.finger);
    os += ", .samplesRemaining = ";
    os += ::android::hardware::toString(o.samplesRemaining);
    os += ", .msg = ";
    os += ::android::hardware::toString(o.msg);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& rhs) {
    if (lhs.finger != rhs.finger) {
        return false;
    }
    if (lhs.samplesRemaining != rhs.samplesRemaining) {
        return false;
    }
    if (lhs.msg != rhs.msg) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintEnroll& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".finger = ";
    os += ::android::hardware::biometrics::fingerprint::V2_1::toString(o.finger);
    os += ", .remainingTemplates = ";
    os += ::android::hardware::toString(o.remainingTemplates);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& rhs) {
    if (lhs.finger != rhs.finger) {
        return false;
    }
    if (lhs.remainingTemplates != rhs.remainingTemplates) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintIterator& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".acquiredInfo = ";
    os += ::android::hardware::biometrics::fingerprint::V2_1::toString(o.acquiredInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& rhs) {
    if (lhs.acquiredInfo != rhs.acquiredInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquired& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".finger = ";
    os += ::android::hardware::biometrics::fingerprint::V2_1::toString(o.finger);
    os += ", .hat = ";
    os += ::android::hardware::toString(o.hat);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& rhs) {
    if (lhs.finger != rhs.finger) {
        return false;
    }
    if (lhs.hat != rhs.hat) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& lhs, const ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAuthenticated& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::ERROR) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::ERROR;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::ACQUIRED) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::ACQUIRED)) {
        os += (first ? "" : " | ");
        os += "ACQUIRED";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::ACQUIRED;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_ENROLLING) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_ENROLLING)) {
        os += (first ? "" : " | ");
        os += "TEMPLATE_ENROLLING";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_ENROLLING;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_REMOVED) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_REMOVED)) {
        os += (first ? "" : " | ");
        os += "TEMPLATE_REMOVED";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_REMOVED;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::AUTHENTICATED) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::AUTHENTICATED)) {
        os += (first ? "" : " | ");
        os += "AUTHENTICATED";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::AUTHENTICATED;
    }
    if ((o & ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_ENUMERATING) == static_cast<int32_t>(::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_ENUMERATING)) {
        os += (first ? "" : " | ");
        os += "TEMPLATE_ENUMERATING";
        first = false;
        flipped |= ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_ENUMERATING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::ERROR) {
        return "ERROR";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::ACQUIRED) {
        return "ACQUIRED";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_ENROLLING) {
        return "TEMPLATE_ENROLLING";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_REMOVED) {
        return "TEMPLATE_REMOVED";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::AUTHENTICATED) {
        return "AUTHENTICATED";
    }
    if (o == ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_ENUMERATING) {
        return "TEMPLATE_ENUMERATING";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V2_1
}  // namespace fingerprint
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
template<> inline constexpr std::array<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus, 13> hidl_enum_values<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus> = {
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_UNKNOWN,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_OK,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOENT,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EINTR,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EIO,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EAGAIN,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOMEM,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EACCES,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EFAULT,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EBUSY,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_EINVAL,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ENOSPC,
    ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus::SYS_ETIMEDOUT,
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
template<> inline constexpr std::array<::android::hardware::biometrics::fingerprint::V2_1::FingerprintError, 9> hidl_enum_values<::android::hardware::biometrics::fingerprint::V2_1::FingerprintError> = {
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_NO_ERROR,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_HW_UNAVAILABLE,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_UNABLE_TO_PROCESS,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_TIMEOUT,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_NO_SPACE,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_CANCELED,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_UNABLE_TO_REMOVE,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_LOCKOUT,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError::ERROR_VENDOR,
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
template<> inline constexpr std::array<::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo, 7> hidl_enum_values<::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo> = {
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_GOOD,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_PARTIAL,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_INSUFFICIENT,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_IMAGER_DIRTY,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_TOO_SLOW,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_TOO_FAST,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo::ACQUIRED_VENDOR,
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
template<> inline constexpr std::array<::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType, 6> hidl_enum_values<::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType> = {
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::ERROR,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::ACQUIRED,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_ENROLLING,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_REMOVED,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::AUTHENTICATED,
    ::android::hardware::biometrics::fingerprint::V2_1::FingerprintMsgType::TEMPLATE_ENUMERATING,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_1_TYPES_H
