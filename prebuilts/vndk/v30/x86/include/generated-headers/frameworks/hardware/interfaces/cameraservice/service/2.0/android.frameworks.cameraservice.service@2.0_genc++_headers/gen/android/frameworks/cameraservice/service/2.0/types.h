#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_0_TYPES_H

#include <android/frameworks/cameraservice/device/2.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class CameraDeviceStatus : int32_t;
struct CameraStatusAndId;

typedef ::android::hardware::hidl_vec<uint8_t> CameraMetadata;

/**
 * CameraDeviceStatus:
 *
 * The current status of the device.
 */
enum class CameraDeviceStatus : int32_t {
    /**
     * Camera is in use by another app and cannot be used exclusively.
     */
    STATUS_NOT_AVAILABLE = -2 /* -2 */,
    /**
     * Use to initialize variables only.
     */
    STATUS_UNKNOWN = -1 /* -1 */,
    /**
     * Device physically unplugged
     */
    STATUS_NOT_PRESENT = 0,
    /**
     * Device physically has been plugged in and the camera can be used
     * exclusively.
     */
    STATUS_PRESENT = 1,
    /**
     * Device physically has been plugged in but it will not be connect-able
     * until enumeration is complete.
     */
    STATUS_ENUMERATING = 2,
};

/**
 * CameraStatusAndId:
 *
 * The camera id and corresponding CameraDeviceStatus.
 */
struct CameraStatusAndId final {
    ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus deviceStatus __attribute__ ((aligned(4)));
    ::android::hardware::hidl_string cameraId __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId, deviceStatus) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId, cameraId) == 8, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId) == 24, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus o);
static inline void PrintTo(::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus lhs, const ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus lhs, const ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_NOT_AVAILABLE) == static_cast<int32_t>(::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_NOT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "STATUS_NOT_AVAILABLE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_NOT_AVAILABLE;
    }
    if ((o & ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_UNKNOWN) == static_cast<int32_t>(::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "STATUS_UNKNOWN";
        first = false;
        flipped |= ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_UNKNOWN;
    }
    if ((o & ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_NOT_PRESENT) == static_cast<int32_t>(::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_NOT_PRESENT)) {
        os += (first ? "" : " | ");
        os += "STATUS_NOT_PRESENT";
        first = false;
        flipped |= ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_NOT_PRESENT;
    }
    if ((o & ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_PRESENT) == static_cast<int32_t>(::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_PRESENT)) {
        os += (first ? "" : " | ");
        os += "STATUS_PRESENT";
        first = false;
        flipped |= ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_PRESENT;
    }
    if ((o & ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_ENUMERATING) == static_cast<int32_t>(::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_ENUMERATING)) {
        os += (first ? "" : " | ");
        os += "STATUS_ENUMERATING";
        first = false;
        flipped |= ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_ENUMERATING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_NOT_AVAILABLE) {
        return "STATUS_NOT_AVAILABLE";
    }
    if (o == ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_UNKNOWN) {
        return "STATUS_UNKNOWN";
    }
    if (o == ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_NOT_PRESENT) {
        return "STATUS_NOT_PRESENT";
    }
    if (o == ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_PRESENT) {
        return "STATUS_PRESENT";
    }
    if (o == ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_ENUMERATING) {
        return "STATUS_ENUMERATING";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".deviceStatus = ";
    os += ::android::frameworks::cameraservice::service::V2_0::toString(o.deviceStatus);
    os += ", .cameraId = ";
    os += ::android::hardware::toString(o.cameraId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& rhs) {
    if (lhs.deviceStatus != rhs.deviceStatus) {
        return false;
    }
    if (lhs.cameraId != rhs.cameraId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_0
}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus, 5> hidl_enum_values<::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus> = {
    ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_NOT_AVAILABLE,
    ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_UNKNOWN,
    ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_NOT_PRESENT,
    ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_PRESENT,
    ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus::STATUS_ENUMERATING,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_0_TYPES_H
