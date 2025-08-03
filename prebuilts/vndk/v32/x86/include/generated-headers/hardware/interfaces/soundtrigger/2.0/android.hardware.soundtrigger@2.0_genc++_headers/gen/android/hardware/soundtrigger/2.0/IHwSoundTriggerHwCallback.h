#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_IHWSOUNDTRIGGERHWCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_IHWSOUNDTRIGGERHWCALLBACK_H

#include <android/hardware/soundtrigger/2.0/ISoundTriggerHwCallback.h>

#include <android/hardware/audio/common/2.0/hwtypes.h>
#include <android/hardware/soundtrigger/2.0/hwtypes.h>
#include <android/hidl/base/1.0/BnHwBase.h>
#include <android/hidl/base/1.0/BpHwBase.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_0
}  // namespace soundtrigger
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_IHWSOUNDTRIGGERHWCALLBACK_H
