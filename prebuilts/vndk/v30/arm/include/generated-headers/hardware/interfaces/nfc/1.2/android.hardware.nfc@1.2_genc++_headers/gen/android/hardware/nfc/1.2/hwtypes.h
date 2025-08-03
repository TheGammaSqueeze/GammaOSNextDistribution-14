#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_HWTYPES_H

#include <android/hardware/nfc/1.2/types.h>

#include <android/hardware/nfc/1.1/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace nfc {
namespace V1_2 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::nfc::V1_2::NfcConfig &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::nfc::V1_2::NfcConfig &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_2
}  // namespace nfc
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_HWTYPES_H
