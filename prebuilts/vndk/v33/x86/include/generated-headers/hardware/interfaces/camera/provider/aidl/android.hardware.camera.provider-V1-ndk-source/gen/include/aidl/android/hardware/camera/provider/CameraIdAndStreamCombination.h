#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/camera/device/StreamConfiguration.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace provider {
class CameraIdAndStreamCombination {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string cameraId;
  ::aidl::android::hardware::camera::device::StreamConfiguration streamConfiguration;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CameraIdAndStreamCombination& rhs) const {
    return std::tie(cameraId, streamConfiguration) != std::tie(rhs.cameraId, rhs.streamConfiguration);
  }
  inline bool operator<(const CameraIdAndStreamCombination& rhs) const {
    return std::tie(cameraId, streamConfiguration) < std::tie(rhs.cameraId, rhs.streamConfiguration);
  }
  inline bool operator<=(const CameraIdAndStreamCombination& rhs) const {
    return std::tie(cameraId, streamConfiguration) <= std::tie(rhs.cameraId, rhs.streamConfiguration);
  }
  inline bool operator==(const CameraIdAndStreamCombination& rhs) const {
    return std::tie(cameraId, streamConfiguration) == std::tie(rhs.cameraId, rhs.streamConfiguration);
  }
  inline bool operator>(const CameraIdAndStreamCombination& rhs) const {
    return std::tie(cameraId, streamConfiguration) > std::tie(rhs.cameraId, rhs.streamConfiguration);
  }
  inline bool operator>=(const CameraIdAndStreamCombination& rhs) const {
    return std::tie(cameraId, streamConfiguration) >= std::tie(rhs.cameraId, rhs.streamConfiguration);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CameraIdAndStreamCombination{";
    os << "cameraId: " << ::android::internal::ToString(cameraId);
    os << ", streamConfiguration: " << ::android::internal::ToString(streamConfiguration);
    os << "}";
    return os.str();
  }
};
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
