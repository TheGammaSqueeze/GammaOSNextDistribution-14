#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_IHWSUPPLICANT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_IHWSUPPLICANT_H

#include <android/hardware/wifi/supplicant/1.0/ISupplicant.h>

#include <android/hardware/wifi/supplicant/1.0/BnHwSupplicantCallback.h>
#include <android/hardware/wifi/supplicant/1.0/BpHwSupplicantCallback.h>
#include <android/hardware/wifi/supplicant/1.0/BnHwSupplicantIface.h>
#include <android/hardware/wifi/supplicant/1.0/BpHwSupplicantIface.h>
#include <android/hardware/wifi/supplicant/1.0/hwtypes.h>
#include <android/hidl/base/1.0/BnHwBase.h>
#include <android/hidl/base/1.0/BpHwBase.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_IHWSUPPLICANT_H
