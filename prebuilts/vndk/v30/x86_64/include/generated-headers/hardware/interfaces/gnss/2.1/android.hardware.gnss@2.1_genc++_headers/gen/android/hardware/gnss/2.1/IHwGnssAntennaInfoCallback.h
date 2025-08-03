#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IHWGNSSANTENNAINFOCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IHWGNSSANTENNAINFOCALLBACK_H

#include <android/hardware/gnss/2.1/IGnssAntennaInfoCallback.h>

#include <android/hidl/base/1.0/BnHwBase.h>
#include <android/hidl/base/1.0/BpHwBase.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::Row &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::gnss::V2_1::IGnssAntennaInfoCallback::GnssAntennaInfo &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_1
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IHWGNSSANTENNAINFOCALLBACK_H
