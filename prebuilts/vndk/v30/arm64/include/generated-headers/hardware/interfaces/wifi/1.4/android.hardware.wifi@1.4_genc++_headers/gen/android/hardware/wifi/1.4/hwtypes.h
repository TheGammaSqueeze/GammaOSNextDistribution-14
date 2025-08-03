#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_4_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_4_HWTYPES_H

#include <android/hardware/wifi/1.4/types.h>

#include <android/hardware/wifi/1.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_4 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::V1_4::RttResult &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::V1_4::RttResult &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_4
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_4_HWTYPES_H
