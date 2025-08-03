#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_1_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_1_HWTYPES_H

#include <android/hardware/gnss/measurement_corrections/1.1/types.h>

#include <android/hardware/gnss/measurement_corrections/1.0/hwtypes.h>
#include <android/hardware/gnss/2.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
namespace V1_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::gnss::measurement_corrections::V1_1::MeasurementCorrections &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_1
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_1_HWTYPES_H
