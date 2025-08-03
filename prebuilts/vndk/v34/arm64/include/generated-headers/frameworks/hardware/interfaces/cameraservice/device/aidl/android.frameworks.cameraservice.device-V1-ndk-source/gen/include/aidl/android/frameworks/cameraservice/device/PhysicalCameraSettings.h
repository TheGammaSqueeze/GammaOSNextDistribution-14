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
class PhysicalCameraSettings {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string id;
  ::aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo settings;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PhysicalCameraSettings& rhs) const {
    return std::tie(id, settings) != std::tie(rhs.id, rhs.settings);
  }
  inline bool operator<(const PhysicalCameraSettings& rhs) const {
    return std::tie(id, settings) < std::tie(rhs.id, rhs.settings);
  }
  inline bool operator<=(const PhysicalCameraSettings& rhs) const {
    return std::tie(id, settings) <= std::tie(rhs.id, rhs.settings);
  }
  inline bool operator==(const PhysicalCameraSettings& rhs) const {
    return std::tie(id, settings) == std::tie(rhs.id, rhs.settings);
  }
  inline bool operator>(const PhysicalCameraSettings& rhs) const {
    return std::tie(id, settings) > std::tie(rhs.id, rhs.settings);
  }
  inline bool operator>=(const PhysicalCameraSettings& rhs) const {
    return std::tie(id, settings) >= std::tie(rhs.id, rhs.settings);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PhysicalCameraSettings{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", settings: " << ::android::internal::ToString(settings);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
