#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_HWTYPES_H

#include <android/hardware/tv/cec/2.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace tv {
namespace cec {
namespace V2_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::tv::cec::V2_0::CecMessage &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::tv::cec::V2_0::CecMessage &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_0
}  // namespace cec
}  // namespace tv
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_HWTYPES_H
