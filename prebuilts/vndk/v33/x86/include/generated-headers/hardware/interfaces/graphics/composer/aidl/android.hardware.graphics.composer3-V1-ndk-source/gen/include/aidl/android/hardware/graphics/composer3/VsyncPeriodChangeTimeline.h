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
namespace graphics {
namespace composer3 {
class VsyncPeriodChangeTimeline {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t newVsyncAppliedTimeNanos = 0L;
  bool refreshRequired = false;
  int64_t refreshTimeNanos = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const VsyncPeriodChangeTimeline& rhs) const {
    return std::tie(newVsyncAppliedTimeNanos, refreshRequired, refreshTimeNanos) != std::tie(rhs.newVsyncAppliedTimeNanos, rhs.refreshRequired, rhs.refreshTimeNanos);
  }
  inline bool operator<(const VsyncPeriodChangeTimeline& rhs) const {
    return std::tie(newVsyncAppliedTimeNanos, refreshRequired, refreshTimeNanos) < std::tie(rhs.newVsyncAppliedTimeNanos, rhs.refreshRequired, rhs.refreshTimeNanos);
  }
  inline bool operator<=(const VsyncPeriodChangeTimeline& rhs) const {
    return std::tie(newVsyncAppliedTimeNanos, refreshRequired, refreshTimeNanos) <= std::tie(rhs.newVsyncAppliedTimeNanos, rhs.refreshRequired, rhs.refreshTimeNanos);
  }
  inline bool operator==(const VsyncPeriodChangeTimeline& rhs) const {
    return std::tie(newVsyncAppliedTimeNanos, refreshRequired, refreshTimeNanos) == std::tie(rhs.newVsyncAppliedTimeNanos, rhs.refreshRequired, rhs.refreshTimeNanos);
  }
  inline bool operator>(const VsyncPeriodChangeTimeline& rhs) const {
    return std::tie(newVsyncAppliedTimeNanos, refreshRequired, refreshTimeNanos) > std::tie(rhs.newVsyncAppliedTimeNanos, rhs.refreshRequired, rhs.refreshTimeNanos);
  }
  inline bool operator>=(const VsyncPeriodChangeTimeline& rhs) const {
    return std::tie(newVsyncAppliedTimeNanos, refreshRequired, refreshTimeNanos) >= std::tie(rhs.newVsyncAppliedTimeNanos, rhs.refreshRequired, rhs.refreshTimeNanos);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "VsyncPeriodChangeTimeline{";
    os << "newVsyncAppliedTimeNanos: " << ::android::internal::ToString(newVsyncAppliedTimeNanos);
    os << ", refreshRequired: " << ::android::internal::ToString(refreshRequired);
    os << ", refreshTimeNanos: " << ::android::internal::ToString(refreshTimeNanos);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
