#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
namespace aidl {
namespace android {
namespace hardware {
namespace power {
class WorkDuration {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t timeStampNanos = 0L;
  int64_t durationNanos = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const WorkDuration& rhs) const {
    return std::tie(timeStampNanos, durationNanos) != std::tie(rhs.timeStampNanos, rhs.durationNanos);
  }
  inline bool operator<(const WorkDuration& rhs) const {
    return std::tie(timeStampNanos, durationNanos) < std::tie(rhs.timeStampNanos, rhs.durationNanos);
  }
  inline bool operator<=(const WorkDuration& rhs) const {
    return std::tie(timeStampNanos, durationNanos) <= std::tie(rhs.timeStampNanos, rhs.durationNanos);
  }
  inline bool operator==(const WorkDuration& rhs) const {
    return std::tie(timeStampNanos, durationNanos) == std::tie(rhs.timeStampNanos, rhs.durationNanos);
  }
  inline bool operator>(const WorkDuration& rhs) const {
    return std::tie(timeStampNanos, durationNanos) > std::tie(rhs.timeStampNanos, rhs.durationNanos);
  }
  inline bool operator>=(const WorkDuration& rhs) const {
    return std::tie(timeStampNanos, durationNanos) >= std::tie(rhs.timeStampNanos, rhs.durationNanos);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "WorkDuration{";
    os << "timeStampNanos: " << ::android::internal::ToString(timeStampNanos);
    os << ", durationNanos: " << ::android::internal::ToString(durationNanos);
    os << "}";
    return os.str();
  }
};
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
