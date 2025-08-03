#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_IHWSOUNDTRIGGERHW_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_IHWSOUNDTRIGGERHW_H

#include <android/hardware/soundtrigger/2.1/ISoundTriggerHw.h>

#include <android/hardware/soundtrigger/2.0/BnHwSoundTriggerHw.h>
#include <android/hardware/soundtrigger/2.0/BpHwSoundTriggerHw.h>
#include <android/hardware/soundtrigger/2.0/BnHwSoundTriggerHwCallback.h>
#include <android/hardware/soundtrigger/2.0/BpHwSoundTriggerHwCallback.h>
#include <android/hardware/soundtrigger/2.0/hwtypes.h>
#include <android/hardware/soundtrigger/2.1/BnHwSoundTriggerHwCallback.h>
#include <android/hardware/soundtrigger/2.1/BpHwSoundTriggerHwCallback.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::SoundModel &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::SoundModel &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::PhraseSoundModel &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::PhraseSoundModel &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::RecognitionConfig &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::RecognitionConfig &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_1
}  // namespace soundtrigger
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_IHWSOUNDTRIGGERHW_H
