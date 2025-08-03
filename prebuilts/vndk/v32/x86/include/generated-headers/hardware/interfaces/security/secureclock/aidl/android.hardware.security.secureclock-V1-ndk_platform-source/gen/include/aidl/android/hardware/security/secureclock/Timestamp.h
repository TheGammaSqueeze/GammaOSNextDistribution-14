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
namespace security {
namespace secureclock {
class Timestamp {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t milliSeconds = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Timestamp& rhs) const {
    return std::tie(milliSeconds) != std::tie(rhs.milliSeconds);
  }
  inline bool operator<(const Timestamp& rhs) const {
    return std::tie(milliSeconds) < std::tie(rhs.milliSeconds);
  }
  inline bool operator<=(const Timestamp& rhs) const {
    return std::tie(milliSeconds) <= std::tie(rhs.milliSeconds);
  }
  inline bool operator==(const Timestamp& rhs) const {
    return std::tie(milliSeconds) == std::tie(rhs.milliSeconds);
  }
  inline bool operator>(const Timestamp& rhs) const {
    return std::tie(milliSeconds) > std::tie(rhs.milliSeconds);
  }
  inline bool operator>=(const Timestamp& rhs) const {
    return std::tie(milliSeconds) >= std::tie(rhs.milliSeconds);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Timestamp{";
    os << "milliSeconds: " << ::android::internal::ToString(milliSeconds);
    os << "}";
    return os.str();
  }
};
}  // namespace secureclock
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
