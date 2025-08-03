#ifndef HIDL_GENERATED_ANDROID_HARDWARE_FASTBOOT_V1_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_FASTBOOT_V1_0_HWTYPES_H

#include <android/hardware/fastboot/1.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace fastboot {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::fastboot::V1_0::Result &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::fastboot::V1_0::Result &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace fastboot
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_FASTBOOT_V1_0_HWTYPES_H
