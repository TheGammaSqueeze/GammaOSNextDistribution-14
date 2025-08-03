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
namespace media {
namespace soundtrigger {
class ConfidenceLevel {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t userId = 0;
  int32_t levelPercent = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ConfidenceLevel& rhs) const {
    return std::tie(userId, levelPercent) != std::tie(rhs.userId, rhs.levelPercent);
  }
  inline bool operator<(const ConfidenceLevel& rhs) const {
    return std::tie(userId, levelPercent) < std::tie(rhs.userId, rhs.levelPercent);
  }
  inline bool operator<=(const ConfidenceLevel& rhs) const {
    return std::tie(userId, levelPercent) <= std::tie(rhs.userId, rhs.levelPercent);
  }
  inline bool operator==(const ConfidenceLevel& rhs) const {
    return std::tie(userId, levelPercent) == std::tie(rhs.userId, rhs.levelPercent);
  }
  inline bool operator>(const ConfidenceLevel& rhs) const {
    return std::tie(userId, levelPercent) > std::tie(rhs.userId, rhs.levelPercent);
  }
  inline bool operator>=(const ConfidenceLevel& rhs) const {
    return std::tie(userId, levelPercent) >= std::tie(rhs.userId, rhs.levelPercent);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ConfidenceLevel{";
    os << "userId: " << ::android::internal::ToString(userId);
    os << ", levelPercent: " << ::android::internal::ToString(levelPercent);
    os << "}";
    return os.str();
  }
};
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
