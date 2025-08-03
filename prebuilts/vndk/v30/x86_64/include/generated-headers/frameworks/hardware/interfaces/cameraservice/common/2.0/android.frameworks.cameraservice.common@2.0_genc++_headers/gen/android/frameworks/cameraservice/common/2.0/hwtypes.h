#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_COMMON_V2_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_COMMON_V2_0_HWTYPES_H

#include <android/frameworks/cameraservice/common/2.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace common {
namespace V2_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::frameworks::cameraservice::common::V2_0::VendorTag &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::frameworks::cameraservice::common::V2_0::VendorTag &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_0
}  // namespace common
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_COMMON_V2_0_HWTYPES_H
