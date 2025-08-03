#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/drm/LogPriority.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class LogMessage {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t timeMs = 0L;
  ::aidl::android::hardware::drm::LogPriority priority = ::aidl::android::hardware::drm::LogPriority(0);
  std::string message;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const LogMessage& rhs) const {
    return std::tie(timeMs, priority, message) != std::tie(rhs.timeMs, rhs.priority, rhs.message);
  }
  inline bool operator<(const LogMessage& rhs) const {
    return std::tie(timeMs, priority, message) < std::tie(rhs.timeMs, rhs.priority, rhs.message);
  }
  inline bool operator<=(const LogMessage& rhs) const {
    return std::tie(timeMs, priority, message) <= std::tie(rhs.timeMs, rhs.priority, rhs.message);
  }
  inline bool operator==(const LogMessage& rhs) const {
    return std::tie(timeMs, priority, message) == std::tie(rhs.timeMs, rhs.priority, rhs.message);
  }
  inline bool operator>(const LogMessage& rhs) const {
    return std::tie(timeMs, priority, message) > std::tie(rhs.timeMs, rhs.priority, rhs.message);
  }
  inline bool operator>=(const LogMessage& rhs) const {
    return std::tie(timeMs, priority, message) >= std::tie(rhs.timeMs, rhs.priority, rhs.message);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "LogMessage{";
    os << "timeMs: " << ::android::internal::ToString(timeMs);
    os << ", priority: " << ::android::internal::ToString(priority);
    os << ", message: " << ::android::internal::ToString(message);
    os << "}";
    return os.str();
  }
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
