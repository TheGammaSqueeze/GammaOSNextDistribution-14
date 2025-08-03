#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/camera/device/StreamRotation.h>
#include <aidl/android/hardware/camera/device/StreamType.h>
#include <aidl/android/hardware/camera/metadata/RequestAvailableDynamicRangeProfilesMap.h>
#include <aidl/android/hardware/camera/metadata/ScalerAvailableStreamUseCases.h>
#include <aidl/android/hardware/camera/metadata/SensorPixelMode.h>
#include <aidl/android/hardware/graphics/common/BufferUsage.h>
#include <aidl/android/hardware/graphics/common/Dataspace.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class Stream {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  ::aidl::android::hardware::camera::device::StreamType streamType = ::aidl::android::hardware::camera::device::StreamType(0);
  int32_t width = 0;
  int32_t height = 0;
  ::aidl::android::hardware::graphics::common::PixelFormat format = ::aidl::android::hardware::graphics::common::PixelFormat(0);
  ::aidl::android::hardware::graphics::common::BufferUsage usage = ::aidl::android::hardware::graphics::common::BufferUsage(0);
  ::aidl::android::hardware::graphics::common::Dataspace dataSpace = ::aidl::android::hardware::graphics::common::Dataspace(0);
  ::aidl::android::hardware::camera::device::StreamRotation rotation = ::aidl::android::hardware::camera::device::StreamRotation(0);
  std::string physicalCameraId;
  int32_t bufferSize = 0;
  int32_t groupId = 0;
  std::vector<::aidl::android::hardware::camera::metadata::SensorPixelMode> sensorPixelModesUsed;
  ::aidl::android::hardware::camera::metadata::RequestAvailableDynamicRangeProfilesMap dynamicRangeProfile = ::aidl::android::hardware::camera::metadata::RequestAvailableDynamicRangeProfilesMap(0);
  ::aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases useCase = ::aidl::android::hardware::camera::metadata::ScalerAvailableStreamUseCases(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Stream& rhs) const {
    return std::tie(id, streamType, width, height, format, usage, dataSpace, rotation, physicalCameraId, bufferSize, groupId, sensorPixelModesUsed, dynamicRangeProfile, useCase) != std::tie(rhs.id, rhs.streamType, rhs.width, rhs.height, rhs.format, rhs.usage, rhs.dataSpace, rhs.rotation, rhs.physicalCameraId, rhs.bufferSize, rhs.groupId, rhs.sensorPixelModesUsed, rhs.dynamicRangeProfile, rhs.useCase);
  }
  inline bool operator<(const Stream& rhs) const {
    return std::tie(id, streamType, width, height, format, usage, dataSpace, rotation, physicalCameraId, bufferSize, groupId, sensorPixelModesUsed, dynamicRangeProfile, useCase) < std::tie(rhs.id, rhs.streamType, rhs.width, rhs.height, rhs.format, rhs.usage, rhs.dataSpace, rhs.rotation, rhs.physicalCameraId, rhs.bufferSize, rhs.groupId, rhs.sensorPixelModesUsed, rhs.dynamicRangeProfile, rhs.useCase);
  }
  inline bool operator<=(const Stream& rhs) const {
    return std::tie(id, streamType, width, height, format, usage, dataSpace, rotation, physicalCameraId, bufferSize, groupId, sensorPixelModesUsed, dynamicRangeProfile, useCase) <= std::tie(rhs.id, rhs.streamType, rhs.width, rhs.height, rhs.format, rhs.usage, rhs.dataSpace, rhs.rotation, rhs.physicalCameraId, rhs.bufferSize, rhs.groupId, rhs.sensorPixelModesUsed, rhs.dynamicRangeProfile, rhs.useCase);
  }
  inline bool operator==(const Stream& rhs) const {
    return std::tie(id, streamType, width, height, format, usage, dataSpace, rotation, physicalCameraId, bufferSize, groupId, sensorPixelModesUsed, dynamicRangeProfile, useCase) == std::tie(rhs.id, rhs.streamType, rhs.width, rhs.height, rhs.format, rhs.usage, rhs.dataSpace, rhs.rotation, rhs.physicalCameraId, rhs.bufferSize, rhs.groupId, rhs.sensorPixelModesUsed, rhs.dynamicRangeProfile, rhs.useCase);
  }
  inline bool operator>(const Stream& rhs) const {
    return std::tie(id, streamType, width, height, format, usage, dataSpace, rotation, physicalCameraId, bufferSize, groupId, sensorPixelModesUsed, dynamicRangeProfile, useCase) > std::tie(rhs.id, rhs.streamType, rhs.width, rhs.height, rhs.format, rhs.usage, rhs.dataSpace, rhs.rotation, rhs.physicalCameraId, rhs.bufferSize, rhs.groupId, rhs.sensorPixelModesUsed, rhs.dynamicRangeProfile, rhs.useCase);
  }
  inline bool operator>=(const Stream& rhs) const {
    return std::tie(id, streamType, width, height, format, usage, dataSpace, rotation, physicalCameraId, bufferSize, groupId, sensorPixelModesUsed, dynamicRangeProfile, useCase) >= std::tie(rhs.id, rhs.streamType, rhs.width, rhs.height, rhs.format, rhs.usage, rhs.dataSpace, rhs.rotation, rhs.physicalCameraId, rhs.bufferSize, rhs.groupId, rhs.sensorPixelModesUsed, rhs.dynamicRangeProfile, rhs.useCase);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Stream{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", streamType: " << ::android::internal::ToString(streamType);
    os << ", width: " << ::android::internal::ToString(width);
    os << ", height: " << ::android::internal::ToString(height);
    os << ", format: " << ::android::internal::ToString(format);
    os << ", usage: " << ::android::internal::ToString(usage);
    os << ", dataSpace: " << ::android::internal::ToString(dataSpace);
    os << ", rotation: " << ::android::internal::ToString(rotation);
    os << ", physicalCameraId: " << ::android::internal::ToString(physicalCameraId);
    os << ", bufferSize: " << ::android::internal::ToString(bufferSize);
    os << ", groupId: " << ::android::internal::ToString(groupId);
    os << ", sensorPixelModesUsed: " << ::android::internal::ToString(sensorPixelModesUsed);
    os << ", dynamicRangeProfile: " << ::android::internal::ToString(dynamicRangeProfile);
    os << ", useCase: " << ::android::internal::ToString(useCase);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
