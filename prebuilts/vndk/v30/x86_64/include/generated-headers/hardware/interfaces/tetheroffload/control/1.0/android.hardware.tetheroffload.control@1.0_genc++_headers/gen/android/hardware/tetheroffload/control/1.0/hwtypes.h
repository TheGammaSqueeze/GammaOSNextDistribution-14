#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_HWTYPES_H

#include <android/hardware/tetheroffload/control/1.0/types.h>


#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace tetheroffload {
namespace control {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace control
}  // namespace tetheroffload
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_HWTYPES_H
