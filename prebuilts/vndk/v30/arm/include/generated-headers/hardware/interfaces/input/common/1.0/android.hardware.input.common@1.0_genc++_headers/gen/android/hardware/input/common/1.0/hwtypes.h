#ifndef HIDL_GENERATED_ANDROID_HARDWARE_INPUT_COMMON_V1_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_INPUT_COMMON_V1_0_HWTYPES_H

#include <android/hardware/input/common/1.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace input {
namespace common {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::input::common::V1_0::PointerCoords &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::input::common::V1_0::PointerCoords &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::input::common::V1_0::VideoFrame &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::input::common::V1_0::VideoFrame &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::input::common::V1_0::MotionEvent &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::input::common::V1_0::MotionEvent &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace common
}  // namespace input
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_INPUT_COMMON_V1_0_HWTYPES_H
