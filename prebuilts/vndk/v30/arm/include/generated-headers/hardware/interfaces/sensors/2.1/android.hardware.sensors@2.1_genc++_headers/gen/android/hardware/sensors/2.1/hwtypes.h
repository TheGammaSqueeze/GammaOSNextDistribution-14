#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_HWTYPES_H

#include <android/hardware/sensors/2.1/types.h>

#include <android/hardware/sensors/1.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace sensors {
namespace V2_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::sensors::V2_1::SensorInfo &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::sensors::V2_1::SensorInfo &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_1
}  // namespace sensors
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_HWTYPES_H
