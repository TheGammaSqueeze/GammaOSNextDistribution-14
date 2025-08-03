#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/camera/device/CameraMetadata.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class PhysicalCameraMetadata {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t fmqMetadataSize = 0L;
  std::string physicalCameraId;
  ::aidl::android::hardware::camera::device::CameraMetadata metadata;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PhysicalCameraMetadata& rhs) const {
    return std::tie(fmqMetadataSize, physicalCameraId, metadata) != std::tie(rhs.fmqMetadataSize, rhs.physicalCameraId, rhs.metadata);
  }
  inline bool operator<(const PhysicalCameraMetadata& rhs) const {
    return std::tie(fmqMetadataSize, physicalCameraId, metadata) < std::tie(rhs.fmqMetadataSize, rhs.physicalCameraId, rhs.metadata);
  }
  inline bool operator<=(const PhysicalCameraMetadata& rhs) const {
    return std::tie(fmqMetadataSize, physicalCameraId, metadata) <= std::tie(rhs.fmqMetadataSize, rhs.physicalCameraId, rhs.metadata);
  }
  inline bool operator==(const PhysicalCameraMetadata& rhs) const {
    return std::tie(fmqMetadataSize, physicalCameraId, metadata) == std::tie(rhs.fmqMetadataSize, rhs.physicalCameraId, rhs.metadata);
  }
  inline bool operator>(const PhysicalCameraMetadata& rhs) const {
    return std::tie(fmqMetadataSize, physicalCameraId, metadata) > std::tie(rhs.fmqMetadataSize, rhs.physicalCameraId, rhs.metadata);
  }
  inline bool operator>=(const PhysicalCameraMetadata& rhs) const {
    return std::tie(fmqMetadataSize, physicalCameraId, metadata) >= std::tie(rhs.fmqMetadataSize, rhs.physicalCameraId, rhs.metadata);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PhysicalCameraMetadata{";
    os << "fmqMetadataSize: " << ::android::internal::ToString(fmqMetadataSize);
    os << ", physicalCameraId: " << ::android::internal::ToString(physicalCameraId);
    os << ", metadata: " << ::android::internal::ToString(metadata);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
