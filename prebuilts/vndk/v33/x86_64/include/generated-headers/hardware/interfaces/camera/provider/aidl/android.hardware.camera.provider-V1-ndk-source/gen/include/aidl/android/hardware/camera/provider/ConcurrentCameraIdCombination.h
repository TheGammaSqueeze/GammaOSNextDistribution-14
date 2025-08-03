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
namespace provider {
class ConcurrentCameraIdCombination {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<std::string> combination;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ConcurrentCameraIdCombination& rhs) const {
    return std::tie(combination) != std::tie(rhs.combination);
  }
  inline bool operator<(const ConcurrentCameraIdCombination& rhs) const {
    return std::tie(combination) < std::tie(rhs.combination);
  }
  inline bool operator<=(const ConcurrentCameraIdCombination& rhs) const {
    return std::tie(combination) <= std::tie(rhs.combination);
  }
  inline bool operator==(const ConcurrentCameraIdCombination& rhs) const {
    return std::tie(combination) == std::tie(rhs.combination);
  }
  inline bool operator>(const ConcurrentCameraIdCombination& rhs) const {
    return std::tie(combination) > std::tie(rhs.combination);
  }
  inline bool operator>=(const ConcurrentCameraIdCombination& rhs) const {
    return std::tie(combination) >= std::tie(rhs.combination);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ConcurrentCameraIdCombination{";
    os << "combination: " << ::android::internal::ToString(combination);
    os << "}";
    return os.str();
  }
};
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
