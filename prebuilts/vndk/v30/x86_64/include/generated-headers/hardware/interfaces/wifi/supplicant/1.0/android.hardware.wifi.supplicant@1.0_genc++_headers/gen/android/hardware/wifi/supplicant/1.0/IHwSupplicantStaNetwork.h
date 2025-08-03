#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_IHWSUPPLICANTSTANETWORK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_IHWSUPPLICANTSTANETWORK_H

#include <android/hardware/wifi/supplicant/1.0/ISupplicantStaNetwork.h>

#include <android/hardware/wifi/supplicant/1.0/BnHwSupplicantNetwork.h>
#include <android/hardware/wifi/supplicant/1.0/BpHwSupplicantNetwork.h>
#include <android/hardware/wifi/supplicant/1.0/BnHwSupplicantStaNetworkCallback.h>
#include <android/hardware/wifi/supplicant/1.0/BpHwSupplicantStaNetworkCallback.h>
#include <android/hardware/wifi/supplicant/1.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::NetworkResponseEapSimUmtsAuthParams &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::NetworkResponseEapSimUmtsAuthParams &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_IHWSUPPLICANTSTANETWORK_H
