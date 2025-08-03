#ifndef HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V3_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V3_0_HWTYPES_H

#include <android/hardware/keymaster/3.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace keymaster {
namespace V3_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::keymaster::V3_0::KeyParameter &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::keymaster::V3_0::KeyParameter &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::keymaster::V3_0::KeyCharacteristics &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::keymaster::V3_0::KeyCharacteristics &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V3_0
}  // namespace keymaster
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V3_0_HWTYPES_H
