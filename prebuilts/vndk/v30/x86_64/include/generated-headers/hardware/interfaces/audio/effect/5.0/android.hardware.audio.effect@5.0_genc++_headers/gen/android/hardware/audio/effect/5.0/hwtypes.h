#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V5_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V5_0_HWTYPES_H

#include <android/hardware/audio/effect/5.0/types.h>

#include <android/hardware/audio/common/5.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V5_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::audio::effect::V5_0::AudioBuffer &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::audio::effect::V5_0::AudioBuffer &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::audio::effect::V5_0::EffectBufferConfig &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::audio::effect::V5_0::EffectBufferConfig &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::audio::effect::V5_0::EffectConfig &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::audio::effect::V5_0::EffectConfig &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V5_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V5_0_HWTYPES_H
