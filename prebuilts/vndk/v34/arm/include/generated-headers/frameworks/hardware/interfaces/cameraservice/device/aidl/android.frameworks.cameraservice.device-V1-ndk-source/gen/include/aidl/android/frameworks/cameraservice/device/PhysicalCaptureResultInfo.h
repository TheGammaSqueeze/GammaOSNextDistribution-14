#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/frameworks/cameraservice/device/CaptureMetadataInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
class PhysicalCaptureResultInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string physicalCameraId;
  ::aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo physicalCameraMetadata;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PhysicalCaptureResultInfo& rhs) const {
    return std::tie(physicalCameraId, physicalCameraMetadata) != std::tie(rhs.physicalCameraId, rhs.physicalCameraMetadata);
  }
  inline bool operator<(const PhysicalCaptureResultInfo& rhs) const {
    return std::tie(physicalCameraId, physicalCameraMetadata) < std::tie(rhs.physicalCameraId, rhs.physicalCameraMetadata);
  }
  inline bool operator<=(const PhysicalCaptureResultInfo& rhs) const {
    return std::tie(physicalCameraId, physicalCameraMetadata) <= std::tie(rhs.physicalCameraId, rhs.physicalCameraMetadata);
  }
  inline bool operator==(const PhysicalCaptureResultInfo& rhs) const {
    return std::tie(physicalCameraId, physicalCameraMetadata) == std::tie(rhs.physicalCameraId, rhs.physicalCameraMetadata);
  }
  inline bool operator>(const PhysicalCaptureResultInfo& rhs) const {
    return std::tie(physicalCameraId, physicalCameraMetadata) > std::tie(rhs.physicalCameraId, rhs.physicalCameraMetadata);
  }
  inline bool operator>=(const PhysicalCaptureResultInfo& rhs) const {
    return std::tie(physicalCameraId, physicalCameraMetadata) >= std::tie(rhs.physicalCameraId, rhs.physicalCameraMetadata);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PhysicalCaptureResultInfo{";
    os << "physicalCameraId: " << ::android::internal::ToString(physicalCameraId);
    os << ", physicalCameraMetadata: " << ::android::internal::ToString(physicalCameraMetadata);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
