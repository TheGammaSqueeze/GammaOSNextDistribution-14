#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_IHWCOMPONENTSTORE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_IHWCOMPONENTSTORE_H

#include <android/hardware/media/c2/1.0/IComponentStore.h>

#include <android/hardware/media/bufferpool/2.0/BnHwClientManager.h>
#include <android/hardware/media/bufferpool/2.0/BpHwClientManager.h>
#include <android/hardware/media/c2/1.0/BnHwComponent.h>
#include <android/hardware/media/c2/1.0/BpHwComponent.h>
#include <android/hardware/media/c2/1.0/BnHwComponentInterface.h>
#include <android/hardware/media/c2/1.0/BpHwComponentInterface.h>
#include <android/hardware/media/c2/1.0/BnHwComponentListener.h>
#include <android/hardware/media/c2/1.0/BpHwComponentListener.h>
#include <android/hardware/media/c2/1.0/BnHwConfigurable.h>
#include <android/hardware/media/c2/1.0/BpHwConfigurable.h>
#include <android/hardware/media/c2/1.0/BnHwInputSurface.h>
#include <android/hardware/media/c2/1.0/BpHwInputSurface.h>
#include <android/hardware/media/c2/1.0/hwtypes.h>
#include <android/hidl/base/1.0/BnHwBase.h>
#include <android/hidl/base/1.0/BpHwBase.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace media {
namespace c2 {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::media::c2::V1_0::IComponentStore::ComponentTraits &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace c2
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_IHWCOMPONENTSTORE_H
