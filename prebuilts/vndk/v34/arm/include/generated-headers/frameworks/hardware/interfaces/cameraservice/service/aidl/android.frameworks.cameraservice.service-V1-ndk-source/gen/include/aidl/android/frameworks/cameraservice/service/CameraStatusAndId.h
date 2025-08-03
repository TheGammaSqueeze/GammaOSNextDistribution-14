#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/frameworks/cameraservice/service/CameraDeviceStatus.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
class CameraStatusAndId {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus deviceStatus = ::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus(0);
  std::string cameraId;
  std::vector<std::string> unavailPhysicalCameraIds;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CameraStatusAndId& rhs) const {
    return std::tie(deviceStatus, cameraId, unavailPhysicalCameraIds) != std::tie(rhs.deviceStatus, rhs.cameraId, rhs.unavailPhysicalCameraIds);
  }
  inline bool operator<(const CameraStatusAndId& rhs) const {
    return std::tie(deviceStatus, cameraId, unavailPhysicalCameraIds) < std::tie(rhs.deviceStatus, rhs.cameraId, rhs.unavailPhysicalCameraIds);
  }
  inline bool operator<=(const CameraStatusAndId& rhs) const {
    return std::tie(deviceStatus, cameraId, unavailPhysicalCameraIds) <= std::tie(rhs.deviceStatus, rhs.cameraId, rhs.unavailPhysicalCameraIds);
  }
  inline bool operator==(const CameraStatusAndId& rhs) const {
    return std::tie(deviceStatus, cameraId, unavailPhysicalCameraIds) == std::tie(rhs.deviceStatus, rhs.cameraId, rhs.unavailPhysicalCameraIds);
  }
  inline bool operator>(const CameraStatusAndId& rhs) const {
    return std::tie(deviceStatus, cameraId, unavailPhysicalCameraIds) > std::tie(rhs.deviceStatus, rhs.cameraId, rhs.unavailPhysicalCameraIds);
  }
  inline bool operator>=(const CameraStatusAndId& rhs) const {
    return std::tie(deviceStatus, cameraId, unavailPhysicalCameraIds) >= std::tie(rhs.deviceStatus, rhs.cameraId, rhs.unavailPhysicalCameraIds);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CameraStatusAndId{";
    os << "deviceStatus: " << ::android::internal::ToString(deviceStatus);
    os << ", cameraId: " << ::android::internal::ToString(cameraId);
    os << ", unavailPhysicalCameraIds: " << ::android::internal::ToString(unavailPhysicalCameraIds);
    os << "}";
    return os.str();
  }
};
}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
