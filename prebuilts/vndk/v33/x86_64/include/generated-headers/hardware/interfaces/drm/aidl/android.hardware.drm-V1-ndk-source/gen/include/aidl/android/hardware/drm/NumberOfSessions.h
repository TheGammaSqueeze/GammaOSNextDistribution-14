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
namespace drm {
class NumberOfSessions {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t currentSessions = 0;
  int32_t maxSessions = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NumberOfSessions& rhs) const {
    return std::tie(currentSessions, maxSessions) != std::tie(rhs.currentSessions, rhs.maxSessions);
  }
  inline bool operator<(const NumberOfSessions& rhs) const {
    return std::tie(currentSessions, maxSessions) < std::tie(rhs.currentSessions, rhs.maxSessions);
  }
  inline bool operator<=(const NumberOfSessions& rhs) const {
    return std::tie(currentSessions, maxSessions) <= std::tie(rhs.currentSessions, rhs.maxSessions);
  }
  inline bool operator==(const NumberOfSessions& rhs) const {
    return std::tie(currentSessions, maxSessions) == std::tie(rhs.currentSessions, rhs.maxSessions);
  }
  inline bool operator>(const NumberOfSessions& rhs) const {
    return std::tie(currentSessions, maxSessions) > std::tie(rhs.currentSessions, rhs.maxSessions);
  }
  inline bool operator>=(const NumberOfSessions& rhs) const {
    return std::tie(currentSessions, maxSessions) >= std::tie(rhs.currentSessions, rhs.maxSessions);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "NumberOfSessions{";
    os << "currentSessions: " << ::android::internal::ToString(currentSessions);
    os << ", maxSessions: " << ::android::internal::ToString(maxSessions);
    os << "}";
    return os.str();
  }
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
