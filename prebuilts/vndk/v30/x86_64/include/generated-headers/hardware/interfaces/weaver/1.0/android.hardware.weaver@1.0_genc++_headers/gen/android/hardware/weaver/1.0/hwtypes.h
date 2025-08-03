#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_HWTYPES_H

#include <android/hardware/weaver/1.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace weaver {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::weaver::V1_0::WeaverReadResponse &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::weaver::V1_0::WeaverReadResponse &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace weaver
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_HWTYPES_H
