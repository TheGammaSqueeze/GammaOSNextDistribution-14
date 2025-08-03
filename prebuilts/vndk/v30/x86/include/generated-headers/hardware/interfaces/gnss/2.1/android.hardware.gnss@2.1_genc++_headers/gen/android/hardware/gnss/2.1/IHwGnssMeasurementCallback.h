#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IHWGNSSMEASUREMENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IHWGNSSMEASUREMENTCALLBACK_H

#include <android/hardware/gnss/2.1/IGnssMeasurementCallback.h>

#include <android/hardware/gnss/1.0/BnHwGnssMeasurementCallback.h>
#include <android/hardware/gnss/1.0/BpHwGnssMeasurementCallback.h>
#include <android/hardware/gnss/2.0/BnHwGnssMeasurementCallback.h>
#include <android/hardware/gnss/2.0/BpHwGnssMeasurementCallback.h>
#include <android/hardware/gnss/2.0/hwtypes.h>
#include <android/hardware/gnss/2.1/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V2_1
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IHWGNSSMEASUREMENTCALLBACK_H
