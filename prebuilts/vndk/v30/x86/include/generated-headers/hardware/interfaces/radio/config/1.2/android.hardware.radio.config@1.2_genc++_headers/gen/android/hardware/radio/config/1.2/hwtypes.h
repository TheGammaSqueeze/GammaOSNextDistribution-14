#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_2_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_2_HWTYPES_H

#include <android/hardware/radio/config/1.2/types.h>

#include <android/hardware/radio/config/1.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace radio {
namespace config {
namespace V1_2 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::radio::config::V1_2::SimSlotStatus &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::radio::config::V1_2::SimSlotStatus &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_2
}  // namespace config
}  // namespace radio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_2_HWTYPES_H
