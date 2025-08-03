#ifndef HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_0_HWTYPES_H

#include <android/hardware/power/1.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace power {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::power::V1_0::PowerStateVoter &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::power::V1_0::PowerStateVoter &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::power::V1_0::PowerStatePlatformSleepState &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::power::V1_0::PowerStatePlatformSleepState &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace power
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_0_HWTYPES_H
