#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_STATS_V1_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_STATS_V1_0_HWTYPES_H

#include <android/frameworks/stats/1.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace frameworks {
namespace stats {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::frameworks::stats::V1_0::ChargeCycles &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::frameworks::stats::V1_0::ChargeCycles &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::frameworks::stats::V1_0::VendorAtom::Value &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::frameworks::stats::V1_0::VendorAtom::Value &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::frameworks::stats::V1_0::VendorAtom &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::frameworks::stats::V1_0::VendorAtom &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace stats
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_STATS_V1_0_HWTYPES_H
