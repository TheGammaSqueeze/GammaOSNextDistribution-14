#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_HWTYPES_H

#include <android/hardware/usb/1.1/types.h>

#include <android/hardware/usb/1.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace usb {
namespace V1_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::usb::V1_1::PortStatus_1_1 &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::usb::V1_1::PortStatus_1_1 &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_1
}  // namespace usb
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_HWTYPES_H
