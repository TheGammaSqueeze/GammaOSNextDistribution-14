#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_IHWSUPPLICANTSTAIFACECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_IHWSUPPLICANTSTAIFACECALLBACK_H

#include <android/hardware/wifi/supplicant/1.0/ISupplicantStaIfaceCallback.h>

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
        const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_IHWSUPPLICANTSTAIFACECALLBACK_H
