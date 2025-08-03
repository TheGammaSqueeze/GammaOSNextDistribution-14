#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_COMMON_V1_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_COMMON_V1_0_HWTYPES_H

#include <android/hardware/camera/common/1.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace camera {
namespace common {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::camera::common::V1_0::VendorTag &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::camera::common::V1_0::VendorTag &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::camera::common::V1_0::VendorTagSection &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::camera::common::V1_0::VendorTagSection &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::camera::common::V1_0::CameraResourceCost &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::camera::common::V1_0::CameraResourceCost &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace common
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_COMMON_V1_0_HWTYPES_H
