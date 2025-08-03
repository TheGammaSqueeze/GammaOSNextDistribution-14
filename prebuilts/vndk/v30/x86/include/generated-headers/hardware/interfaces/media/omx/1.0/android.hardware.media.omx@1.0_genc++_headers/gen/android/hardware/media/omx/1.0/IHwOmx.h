#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_IHWOMX_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_IHWOMX_H

#include <android/hardware/media/omx/1.0/IOmx.h>

#include <android/hardware/graphics/bufferqueue/1.0/BnHwGraphicBufferProducer.h>
#include <android/hardware/graphics/bufferqueue/1.0/BpHwGraphicBufferProducer.h>
#include <android/hardware/media/omx/1.0/BnHwGraphicBufferSource.h>
#include <android/hardware/media/omx/1.0/BpHwGraphicBufferSource.h>
#include <android/hardware/media/omx/1.0/BnHwOmxNode.h>
#include <android/hardware/media/omx/1.0/BpHwOmxNode.h>
#include <android/hardware/media/omx/1.0/BnHwOmxObserver.h>
#include <android/hardware/media/omx/1.0/BpHwOmxObserver.h>
#include <android/hardware/media/omx/1.0/hwtypes.h>
#include <android/hidl/base/1.0/BnHwBase.h>
#include <android/hidl/base/1.0/BpHwBase.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace media {
namespace omx {
namespace V1_0 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::media::omx::V1_0::IOmx::ComponentInfo &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::media::omx::V1_0::IOmx::ComponentInfo &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_0
}  // namespace omx
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_IHWOMX_H
