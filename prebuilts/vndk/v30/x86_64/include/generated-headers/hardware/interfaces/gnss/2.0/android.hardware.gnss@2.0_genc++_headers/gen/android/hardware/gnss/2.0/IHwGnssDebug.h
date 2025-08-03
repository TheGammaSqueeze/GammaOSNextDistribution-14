#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_IHWGNSSDEBUG_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_IHWGNSSDEBUG_H

#include <android/hardware/gnss/2.0/IGnssDebug.h>

#include <android/hardware/gnss/1.0/BnHwGnssDebug.h>
#include <android/hardware/gnss/1.0/BpHwGnssDebug.h>
#include <android/hardware/gnss/2.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::gnss::V2_0::IGnssDebug::DebugData &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::gnss::V2_0::IGnssDebug::DebugData &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_IHWGNSSDEBUG_H
