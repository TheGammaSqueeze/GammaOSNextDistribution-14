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
namespace gnss {
class ElapsedRealtime {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t flags = 0;
  int64_t timestampNs = 0L;
  double timeUncertaintyNs = 0.000000;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ElapsedRealtime& rhs) const {
    return std::tie(flags, timestampNs, timeUncertaintyNs) != std::tie(rhs.flags, rhs.timestampNs, rhs.timeUncertaintyNs);
  }
  inline bool operator<(const ElapsedRealtime& rhs) const {
    return std::tie(flags, timestampNs, timeUncertaintyNs) < std::tie(rhs.flags, rhs.timestampNs, rhs.timeUncertaintyNs);
  }
  inline bool operator<=(const ElapsedRealtime& rhs) const {
    return std::tie(flags, timestampNs, timeUncertaintyNs) <= std::tie(rhs.flags, rhs.timestampNs, rhs.timeUncertaintyNs);
  }
  inline bool operator==(const ElapsedRealtime& rhs) const {
    return std::tie(flags, timestampNs, timeUncertaintyNs) == std::tie(rhs.flags, rhs.timestampNs, rhs.timeUncertaintyNs);
  }
  inline bool operator>(const ElapsedRealtime& rhs) const {
    return std::tie(flags, timestampNs, timeUncertaintyNs) > std::tie(rhs.flags, rhs.timestampNs, rhs.timeUncertaintyNs);
  }
  inline bool operator>=(const ElapsedRealtime& rhs) const {
    return std::tie(flags, timestampNs, timeUncertaintyNs) >= std::tie(rhs.flags, rhs.timestampNs, rhs.timeUncertaintyNs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { HAS_TIMESTAMP_NS = 1 };
  enum : int32_t { HAS_TIME_UNCERTAINTY_NS = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "ElapsedRealtime{";
    os << "flags: " << ::android::internal::ToString(flags);
    os << ", timestampNs: " << ::android::internal::ToString(timestampNs);
    os << ", timeUncertaintyNs: " << ::android::internal::ToString(timeUncertaintyNs);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
