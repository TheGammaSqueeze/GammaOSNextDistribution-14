#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/camera/device/OfflineRequest.h>
#include <aidl/android/hardware/camera/device/OfflineStream.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class CameraOfflineSessionInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::camera::device::OfflineStream> offlineStreams;
  std::vector<::aidl::android::hardware::camera::device::OfflineRequest> offlineRequests;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CameraOfflineSessionInfo& rhs) const {
    return std::tie(offlineStreams, offlineRequests) != std::tie(rhs.offlineStreams, rhs.offlineRequests);
  }
  inline bool operator<(const CameraOfflineSessionInfo& rhs) const {
    return std::tie(offlineStreams, offlineRequests) < std::tie(rhs.offlineStreams, rhs.offlineRequests);
  }
  inline bool operator<=(const CameraOfflineSessionInfo& rhs) const {
    return std::tie(offlineStreams, offlineRequests) <= std::tie(rhs.offlineStreams, rhs.offlineRequests);
  }
  inline bool operator==(const CameraOfflineSessionInfo& rhs) const {
    return std::tie(offlineStreams, offlineRequests) == std::tie(rhs.offlineStreams, rhs.offlineRequests);
  }
  inline bool operator>(const CameraOfflineSessionInfo& rhs) const {
    return std::tie(offlineStreams, offlineRequests) > std::tie(rhs.offlineStreams, rhs.offlineRequests);
  }
  inline bool operator>=(const CameraOfflineSessionInfo& rhs) const {
    return std::tie(offlineStreams, offlineRequests) >= std::tie(rhs.offlineStreams, rhs.offlineRequests);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CameraOfflineSessionInfo{";
    os << "offlineStreams: " << ::android::internal::ToString(offlineStreams);
    os << ", offlineRequests: " << ::android::internal::ToString(offlineRequests);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
