#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/frameworks/cameraservice/device/PhysicalCameraSettings.h>
#include <aidl/android/frameworks/cameraservice/device/StreamAndWindowId.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::frameworks::cameraservice::device {
class PhysicalCameraSettings;
class StreamAndWindowId;
}  // namespace aidl::android::frameworks::cameraservice::device
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
class CaptureRequest {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::frameworks::cameraservice::device::PhysicalCameraSettings> physicalCameraSettings;
  std::vector<::aidl::android::frameworks::cameraservice::device::StreamAndWindowId> streamAndWindowIds;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CaptureRequest& rhs) const {
    return std::tie(physicalCameraSettings, streamAndWindowIds) != std::tie(rhs.physicalCameraSettings, rhs.streamAndWindowIds);
  }
  inline bool operator<(const CaptureRequest& rhs) const {
    return std::tie(physicalCameraSettings, streamAndWindowIds) < std::tie(rhs.physicalCameraSettings, rhs.streamAndWindowIds);
  }
  inline bool operator<=(const CaptureRequest& rhs) const {
    return std::tie(physicalCameraSettings, streamAndWindowIds) <= std::tie(rhs.physicalCameraSettings, rhs.streamAndWindowIds);
  }
  inline bool operator==(const CaptureRequest& rhs) const {
    return std::tie(physicalCameraSettings, streamAndWindowIds) == std::tie(rhs.physicalCameraSettings, rhs.streamAndWindowIds);
  }
  inline bool operator>(const CaptureRequest& rhs) const {
    return std::tie(physicalCameraSettings, streamAndWindowIds) > std::tie(rhs.physicalCameraSettings, rhs.streamAndWindowIds);
  }
  inline bool operator>=(const CaptureRequest& rhs) const {
    return std::tie(physicalCameraSettings, streamAndWindowIds) >= std::tie(rhs.physicalCameraSettings, rhs.streamAndWindowIds);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CaptureRequest{";
    os << "physicalCameraSettings: " << ::android::internal::ToString(physicalCameraSettings);
    os << ", streamAndWindowIds: " << ::android::internal::ToString(streamAndWindowIds);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
