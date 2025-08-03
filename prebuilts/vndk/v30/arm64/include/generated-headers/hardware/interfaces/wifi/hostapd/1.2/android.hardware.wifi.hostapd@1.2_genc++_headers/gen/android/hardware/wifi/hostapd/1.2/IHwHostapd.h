#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_2_IHWHOSTAPD_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_2_IHWHOSTAPD_H

#include <android/hardware/wifi/hostapd/1.2/IHostapd.h>

#include <android/hardware/wifi/hostapd/1.0/BnHwHostapd.h>
#include <android/hardware/wifi/hostapd/1.0/BpHwHostapd.h>
#include <android/hardware/wifi/hostapd/1.1/BnHwHostapd.h>
#include <android/hardware/wifi/hostapd/1.1/BpHwHostapd.h>
#include <android/hardware/wifi/hostapd/1.2/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_2 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_2
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_2_IHWHOSTAPD_H
