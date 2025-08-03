#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_HWTYPES_H

#include <android/hardware/broadcastradio/1.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::broadcastradio::V1_0::BandConfig &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::broadcastradio::V1_0::BandConfig &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::broadcastradio::V1_0::Properties &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::broadcastradio::V1_0::Properties &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::broadcastradio::V1_0::MetaData &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::broadcastradio::V1_0::MetaData &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::broadcastradio::V1_0::ProgramInfo &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::broadcastradio::V1_0::ProgramInfo &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_HWTYPES_H
