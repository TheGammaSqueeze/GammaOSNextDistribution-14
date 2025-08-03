#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace common {
class CameraResourceCost {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t resourceCost = 0;
  std::vector<std::string> conflictingDevices;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CameraResourceCost& rhs) const {
    return std::tie(resourceCost, conflictingDevices) != std::tie(rhs.resourceCost, rhs.conflictingDevices);
  }
  inline bool operator<(const CameraResourceCost& rhs) const {
    return std::tie(resourceCost, conflictingDevices) < std::tie(rhs.resourceCost, rhs.conflictingDevices);
  }
  inline bool operator<=(const CameraResourceCost& rhs) const {
    return std::tie(resourceCost, conflictingDevices) <= std::tie(rhs.resourceCost, rhs.conflictingDevices);
  }
  inline bool operator==(const CameraResourceCost& rhs) const {
    return std::tie(resourceCost, conflictingDevices) == std::tie(rhs.resourceCost, rhs.conflictingDevices);
  }
  inline bool operator>(const CameraResourceCost& rhs) const {
    return std::tie(resourceCost, conflictingDevices) > std::tie(rhs.resourceCost, rhs.conflictingDevices);
  }
  inline bool operator>=(const CameraResourceCost& rhs) const {
    return std::tie(resourceCost, conflictingDevices) >= std::tie(rhs.resourceCost, rhs.conflictingDevices);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CameraResourceCost{";
    os << "resourceCost: " << ::android::internal::ToString(resourceCost);
    os << ", conflictingDevices: " << ::android::internal::ToString(conflictingDevices);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
