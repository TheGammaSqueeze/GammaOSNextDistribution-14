#ifndef HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V2_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V2_0_HWTYPES_H

#include <android/hardware/thermal/2.0/types.h>

#include <android/hardware/thermal/1.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace thermal {
namespace V2_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::thermal::V2_0::TemperatureThreshold &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::thermal::V2_0::TemperatureThreshold &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::thermal::V2_0::Temperature &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::thermal::V2_0::Temperature &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::thermal::V2_0::CoolingDevice &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::thermal::V2_0::CoolingDevice &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_0
}  // namespace thermal
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V2_0_HWTYPES_H
