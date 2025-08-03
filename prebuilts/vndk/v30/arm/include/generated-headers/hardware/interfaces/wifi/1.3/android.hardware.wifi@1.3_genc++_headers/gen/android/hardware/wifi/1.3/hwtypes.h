#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_3_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_3_HWTYPES_H

#include <android/hardware/wifi/1.3/types.h>

#include <android/hardware/wifi/1.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_3 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::V1_3::StaLinkLayerStats &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::V1_3::StaLinkLayerStats &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_3
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_3_HWTYPES_H
