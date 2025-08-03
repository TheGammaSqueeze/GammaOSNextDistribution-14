#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_6_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_6_TYPES_H

#include <android/hardware/camera/device/3.4/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace V2_6 {

// Forward declaration for forward reference support:
struct CameraIdAndStreamCombination;

/**
 * CameraIdAndStreamCombination:
 * Pairs the cameraId and the StreamConfiguration to be
 * tested with other concurrent camera id and StreamConfigurations
 */
struct CameraIdAndStreamCombination final {
    ::android::hardware::hidl_string cameraId __attribute__ ((aligned(8)));
    ::android::hardware::camera::device::V3_4::StreamConfiguration streamConfiguration __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination, cameraId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination, streamConfiguration) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination) == 56, "wrong size");
static_assert(__alignof(::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& o);
static inline void PrintTo(const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& lhs, const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& rhs);
static inline bool operator!=(const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& lhs, const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cameraId = ";
    os += ::android::hardware::toString(o.cameraId);
    os += ", .streamConfiguration = ";
    os += ::android::hardware::camera::device::V3_4::toString(o.streamConfiguration);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& lhs, const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& rhs) {
    if (lhs.cameraId != rhs.cameraId) {
        return false;
    }
    if (lhs.streamConfiguration != rhs.streamConfiguration) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& lhs, const ::android::hardware::camera::provider::V2_6::CameraIdAndStreamCombination& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_6
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_6_TYPES_H
