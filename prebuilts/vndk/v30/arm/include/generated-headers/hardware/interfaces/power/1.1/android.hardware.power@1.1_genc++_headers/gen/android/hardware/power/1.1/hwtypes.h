#ifndef HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_1_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_1_HWTYPES_H

#include <android/hardware/power/1.1/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace power {
namespace V1_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::power::V1_1::PowerStateSubsystemSleepState &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::power::V1_1::PowerStateSubsystem &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::power::V1_1::PowerStateSubsystem &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_1
}  // namespace power
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_1_HWTYPES_H
