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
namespace wifi {
namespace supplicant {
class FreqRange {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t min = 0;
  int32_t max = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const FreqRange& rhs) const {
    return std::tie(min, max) != std::tie(rhs.min, rhs.max);
  }
  inline bool operator<(const FreqRange& rhs) const {
    return std::tie(min, max) < std::tie(rhs.min, rhs.max);
  }
  inline bool operator<=(const FreqRange& rhs) const {
    return std::tie(min, max) <= std::tie(rhs.min, rhs.max);
  }
  inline bool operator==(const FreqRange& rhs) const {
    return std::tie(min, max) == std::tie(rhs.min, rhs.max);
  }
  inline bool operator>(const FreqRange& rhs) const {
    return std::tie(min, max) > std::tie(rhs.min, rhs.max);
  }
  inline bool operator>=(const FreqRange& rhs) const {
    return std::tie(min, max) >= std::tie(rhs.min, rhs.max);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "FreqRange{";
    os << "min: " << ::android::internal::ToString(min);
    os << ", max: " << ::android::internal::ToString(max);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
