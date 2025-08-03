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
class VsyncPeriodChangeConstraints {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t desiredTimeNanos = 0L;
  bool seamlessRequired = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const VsyncPeriodChangeConstraints& rhs) const {
    return std::tie(desiredTimeNanos, seamlessRequired) != std::tie(rhs.desiredTimeNanos, rhs.seamlessRequired);
  }
  inline bool operator<(const VsyncPeriodChangeConstraints& rhs) const {
    return std::tie(desiredTimeNanos, seamlessRequired) < std::tie(rhs.desiredTimeNanos, rhs.seamlessRequired);
  }
  inline bool operator<=(const VsyncPeriodChangeConstraints& rhs) const {
    return std::tie(desiredTimeNanos, seamlessRequired) <= std::tie(rhs.desiredTimeNanos, rhs.seamlessRequired);
  }
  inline bool operator==(const VsyncPeriodChangeConstraints& rhs) const {
    return std::tie(desiredTimeNanos, seamlessRequired) == std::tie(rhs.desiredTimeNanos, rhs.seamlessRequired);
  }
  inline bool operator>(const VsyncPeriodChangeConstraints& rhs) const {
    return std::tie(desiredTimeNanos, seamlessRequired) > std::tie(rhs.desiredTimeNanos, rhs.seamlessRequired);
  }
  inline bool operator>=(const VsyncPeriodChangeConstraints& rhs) const {
    return std::tie(desiredTimeNanos, seamlessRequired) >= std::tie(rhs.desiredTimeNanos, rhs.seamlessRequired);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "VsyncPeriodChangeConstraints{";
    os << "desiredTimeNanos: " << ::android::internal::ToString(desiredTimeNanos);
    os << ", seamlessRequired: " << ::android::internal::ToString(seamlessRequired);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
