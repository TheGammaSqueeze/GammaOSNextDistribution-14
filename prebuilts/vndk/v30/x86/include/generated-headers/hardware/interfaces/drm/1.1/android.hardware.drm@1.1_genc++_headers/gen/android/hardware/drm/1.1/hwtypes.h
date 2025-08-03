#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_1_HWTYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_1_HWTYPES_H

#include <android/hardware/drm/1.1/types.h>

#include <android/hardware/drm/1.0/hwtypes.h>

#include <hidl/Status.h>
#include <hwbinder/IBinder.h>
#include <hwbinder/Parcel.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_1 {
::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::drm::V1_1::DrmMetricGroup::Attribute &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::drm::V1_1::DrmMetricGroup::Value &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::drm::V1_1::DrmMetricGroup::Value &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::drm::V1_1::DrmMetricGroup::Metric &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::drm::V1_1::DrmMetricGroup &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::drm::V1_1::DrmMetricGroup &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t readEmbeddedFromParcel(
        const ::android::hardware::drm::V1_1::SecureStopRelease &obj,
        const ::android::hardware::Parcel &parcel,
        size_t parentHandle,
        size_t parentOffset);

::android::status_t writeEmbeddedToParcel(
        const ::android::hardware::drm::V1_1::SecureStopRelease &obj,
        ::android::hardware::Parcel *parcel,
        size_t parentHandle,
        size_t parentOffset);

}  // namespace V1_1
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_1_HWTYPES_H
