#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_3_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_3_HWTYPES_H

#include <android/hardware/soundtrigger/2.3/types.h>

#include <android/hardware/soundtrigger/2.0/BnHwSoundTriggerHw.h>
#include <android/hardware/soundtrigger/2.0/BpHwSoundTriggerHw.h>
#include <android/hardware/soundtrigger/2.1/BnHwSoundTriggerHw.h>
#include <android/hardware/soundtrigger/2.1/BpHwSoundTriggerHw.h>
#include <android/hidl/safe_union/1.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_3 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_3::Properties &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_3::Properties &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_3::RecognitionConfig &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_3::RecognitionConfig &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_3
}  // namespace soundtrigger
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_3_HWTYPES_H
