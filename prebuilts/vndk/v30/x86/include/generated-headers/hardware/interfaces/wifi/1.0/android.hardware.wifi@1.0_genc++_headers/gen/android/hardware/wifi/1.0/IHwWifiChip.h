#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_IHWWIFICHIP_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_IHWWIFICHIP_H

#include <android/hardware/wifi/1.0/IWifiChip.h>

#include <android/hardware/wifi/1.0/BnHwWifiApIface.h>
#include <android/hardware/wifi/1.0/BpHwWifiApIface.h>
#include <android/hardware/wifi/1.0/BnHwWifiChipEventCallback.h>
#include <android/hardware/wifi/1.0/BpHwWifiChipEventCallback.h>
#include <android/hardware/wifi/1.0/BnHwWifiIface.h>
#include <android/hardware/wifi/1.0/BpHwWifiIface.h>
#include <android/hardware/wifi/1.0/BnHwWifiNanIface.h>
#include <android/hardware/wifi/1.0/BpHwWifiNanIface.h>
#include <android/hardware/wifi/1.0/BnHwWifiP2pIface.h>
#include <android/hardware/wifi/1.0/BpHwWifiP2pIface.h>
#include <android/hardware/wifi/1.0/BnHwWifiRttController.h>
#include <android/hardware/wifi/1.0/BpHwWifiRttController.h>
#include <android/hardware/wifi/1.0/BnHwWifiStaIface.h>
#include <android/hardware/wifi/1.0/BpHwWifiStaIface.h>
#include <android/hardware/wifi/1.0/hwtypes.h>
#include <android/hidl/base/1.0/BnHwBase.h>
#include <android/hidl/base/1.0/BpHwBase.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_IHWWIFICHIP_H
