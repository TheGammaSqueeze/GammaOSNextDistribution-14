#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_HWTYPES_H

#include <android/hardware/automotive/evs/1.1/types.h>

#include <android/hardware/automotive/evs/1.0/hwtypes.h>
#include <android/hardware/camera/device/3.2/hwtypes.h>
#include <android/hardware/graphics/common/1.2/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::automotive::evs::V1_1::CameraDesc &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::automotive::evs::V1_1::CameraDesc &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::automotive::evs::V1_1::BufferDesc &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::automotive::evs::V1_1::BufferDesc &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::automotive::evs::V1_1::EvsEventDesc &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::automotive::evs::V1_1::EvsEventDesc &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_1
}  // namespace evs
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_HWTYPES_H
