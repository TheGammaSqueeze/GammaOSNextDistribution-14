#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_COMMON_V4_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_COMMON_V4_0_HWTYPES_H

#include <android/hardware/audio/common/4.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace audio {
namespace common {
namespace V4_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::audio::common::V4_0::AudioPort &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::audio::common::V4_0::AudioPort &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V4_0
}  // namespace common
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_COMMON_V4_0_HWTYPES_H
