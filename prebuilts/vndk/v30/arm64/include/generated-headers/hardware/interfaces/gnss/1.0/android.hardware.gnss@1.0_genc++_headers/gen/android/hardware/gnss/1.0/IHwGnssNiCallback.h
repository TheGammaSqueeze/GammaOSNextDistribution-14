#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IHWGNSSNICALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IHWGNSSNICALLBACK_H

#include <android/hardware/gnss/1.0/IGnssNiCallback.h>

#include <android/hidl/base/1.0/BnHwBase.h>
#include <android/hidl/base/1.0/BpHwBase.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::gnss::V1_0::IGnssNiCallback::GnssNiNotification &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::gnss::V1_0::IGnssNiCallback::GnssNiNotification &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IHWGNSSNICALLBACK_H
