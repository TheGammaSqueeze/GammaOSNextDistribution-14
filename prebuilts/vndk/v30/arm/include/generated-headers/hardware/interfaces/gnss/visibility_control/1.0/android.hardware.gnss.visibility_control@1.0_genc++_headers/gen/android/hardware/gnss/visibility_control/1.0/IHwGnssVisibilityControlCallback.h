#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_VISIBILITY_CONTROL_V1_0_IHWGNSSVISIBILITYCONTROLCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_VISIBILITY_CONTROL_V1_0_IHWGNSSVISIBILITYCONTROLCALLBACK_H

#include <android/hardware/gnss/visibility_control/1.0/IGnssVisibilityControlCallback.h>

#include <android/hidl/base/1.0/BnHwBase.h>
#include <android/hidl/base/1.0/BpHwBase.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace gnss {
namespace visibility_control {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControlCallback::NfwNotification &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::gnss::visibility_control::V1_0::IGnssVisibilityControlCallback::NfwNotification &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace visibility_control
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_VISIBILITY_CONTROL_V1_0_IHWGNSSVISIBILITYCONTROLCALLBACK_H
