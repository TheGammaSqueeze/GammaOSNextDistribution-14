#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V1_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V1_0_HWTYPES_H

#include <android/hardware/sensors/1.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace sensors {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::sensors::V1_0::SensorInfo &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::sensors::V1_0::SensorInfo &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::sensors::V1_0::SharedMemInfo &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::sensors::V1_0::SharedMemInfo &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace sensors
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V1_0_HWTYPES_H
