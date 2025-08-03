#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_1_TYPES_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_1_TYPES_H

#include <android/frameworks/cameraservice/service/2.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
namespace V2_1 {

// Forward declaration for forward reference support:
struct CameraStatusAndId;
struct PhysicalCameraStatusAndId;

/**
 * CameraStatusAndId:
 *
 * The camera Id and its corresponding CameraDeviceStatus
 *
 * This version extends the @2_0::CameraStatusAndId with the
 * unavailPhysicalCameraIds field.
 *
 */
struct CameraStatusAndId final {
    /**
     * The definition of CameraStatusAndId from prior version.
     */
    ::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId v2_0 __attribute__ ((aligned(8)));
    /**
     * The physical cameras that are unavailable to use (via physical streams)
     * for this logical multi-camera.
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> unavailPhysicalCameraIds __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId, v2_0) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId, unavailPhysicalCameraIds) == 24, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId) == 40, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId) == 8, "wrong alignment");

/**
 * PhysicalCameraStatusAndId:
 *
 * The physical camera id backing a logical multi-camera, and its
 * corresponding CameraDeviceStatus.
 */
struct PhysicalCameraStatusAndId final {
    ::android::frameworks::cameraservice::service::V2_0::CameraDeviceStatus deviceStatus __attribute__ ((aligned(4)));
    ::android::hardware::hidl_string cameraId __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string physicalCameraId __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId, deviceStatus) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId, cameraId) == 8, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId, physicalCameraId) == 24, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId) == 40, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& rhs);

static inline std::string toString(const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v2_0 = ";
    os += ::android::frameworks::cameraservice::service::V2_0::toString(o.v2_0);
    os += ", .unavailPhysicalCameraIds = ";
    os += ::android::hardware::toString(o.unavailPhysicalCameraIds);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& rhs) {
    if (lhs.v2_0 != rhs.v2_0) {
        return false;
    }
    if (lhs.unavailPhysicalCameraIds != rhs.unavailPhysicalCameraIds) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_1::CameraStatusAndId& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".deviceStatus = ";
    os += ::android::frameworks::cameraservice::service::V2_0::toString(o.deviceStatus);
    os += ", .cameraId = ";
    os += ::android::hardware::toString(o.cameraId);
    os += ", .physicalCameraId = ";
    os += ::android::hardware::toString(o.physicalCameraId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& rhs) {
    if (lhs.deviceStatus != rhs.deviceStatus) {
        return false;
    }
    if (lhs.cameraId != rhs.cameraId) {
        return false;
    }
    if (lhs.physicalCameraId != rhs.physicalCameraId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& lhs, const ::android::frameworks::cameraservice::service::V2_1::PhysicalCameraStatusAndId& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_1
}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_1_TYPES_H
