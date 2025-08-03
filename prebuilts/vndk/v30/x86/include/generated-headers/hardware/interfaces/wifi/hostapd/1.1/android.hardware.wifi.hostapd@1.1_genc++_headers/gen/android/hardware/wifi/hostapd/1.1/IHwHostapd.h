#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_1_IHWHOSTAPD_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_1_IHWHOSTAPD_H

#include <android/hardware/wifi/hostapd/1.1/IHostapd.h>

#include <android/hardware/wifi/hostapd/1.0/BnHwHostapd.h>
#include <android/hardware/wifi/hostapd/1.0/BpHwHostapd.h>
#include <android/hardware/wifi/hostapd/1.0/hwtypes.h>
#include <android/hardware/wifi/hostapd/1.1/BnHwHostapdCallback.h>
#include <android/hardware/wifi/hostapd/1.1/BpHwHostapdCallback.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_1
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_1_IHWHOSTAPD_H
