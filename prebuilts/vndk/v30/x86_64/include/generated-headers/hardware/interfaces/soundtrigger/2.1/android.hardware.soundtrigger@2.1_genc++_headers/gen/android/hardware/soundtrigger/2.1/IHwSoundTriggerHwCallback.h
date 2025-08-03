#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_IHWSOUNDTRIGGERHWCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_IHWSOUNDTRIGGERHWCALLBACK_H

#include <android/hardware/soundtrigger/2.1/ISoundTriggerHwCallback.h>

#include <android/hardware/soundtrigger/2.0/BnHwSoundTriggerHwCallback.h>
#include <android/hardware/soundtrigger/2.0/BpHwSoundTriggerHwCallback.h>
#include <android/hardware/soundtrigger/2.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::RecognitionEvent &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::RecognitionEvent &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::PhraseRecognitionEvent &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::PhraseRecognitionEvent &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::ModelEvent &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::ModelEvent &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_1
}  // namespace soundtrigger
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_IHWSOUNDTRIGGERHWCALLBACK_H
