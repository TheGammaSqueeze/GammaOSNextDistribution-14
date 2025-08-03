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
namespace stats {
class StateResidency {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  int64_t totalTimeInStateMs = 0L;
  int64_t totalStateEntryCount = 0L;
  int64_t lastEntryTimestampMs = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const StateResidency& rhs) const {
    return std::tie(id, totalTimeInStateMs, totalStateEntryCount, lastEntryTimestampMs) != std::tie(rhs.id, rhs.totalTimeInStateMs, rhs.totalStateEntryCount, rhs.lastEntryTimestampMs);
  }
  inline bool operator<(const StateResidency& rhs) const {
    return std::tie(id, totalTimeInStateMs, totalStateEntryCount, lastEntryTimestampMs) < std::tie(rhs.id, rhs.totalTimeInStateMs, rhs.totalStateEntryCount, rhs.lastEntryTimestampMs);
  }
  inline bool operator<=(const StateResidency& rhs) const {
    return std::tie(id, totalTimeInStateMs, totalStateEntryCount, lastEntryTimestampMs) <= std::tie(rhs.id, rhs.totalTimeInStateMs, rhs.totalStateEntryCount, rhs.lastEntryTimestampMs);
  }
  inline bool operator==(const StateResidency& rhs) const {
    return std::tie(id, totalTimeInStateMs, totalStateEntryCount, lastEntryTimestampMs) == std::tie(rhs.id, rhs.totalTimeInStateMs, rhs.totalStateEntryCount, rhs.lastEntryTimestampMs);
  }
  inline bool operator>(const StateResidency& rhs) const {
    return std::tie(id, totalTimeInStateMs, totalStateEntryCount, lastEntryTimestampMs) > std::tie(rhs.id, rhs.totalTimeInStateMs, rhs.totalStateEntryCount, rhs.lastEntryTimestampMs);
  }
  inline bool operator>=(const StateResidency& rhs) const {
    return std::tie(id, totalTimeInStateMs, totalStateEntryCount, lastEntryTimestampMs) >= std::tie(rhs.id, rhs.totalTimeInStateMs, rhs.totalStateEntryCount, rhs.lastEntryTimestampMs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "StateResidency{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", totalTimeInStateMs: " << ::android::internal::ToString(totalTimeInStateMs);
    os << ", totalStateEntryCount: " << ::android::internal::ToString(totalStateEntryCount);
    os << ", lastEntryTimestampMs: " << ::android::internal::ToString(lastEntryTimestampMs);
    os << "}";
    return os.str();
  }
};
}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
