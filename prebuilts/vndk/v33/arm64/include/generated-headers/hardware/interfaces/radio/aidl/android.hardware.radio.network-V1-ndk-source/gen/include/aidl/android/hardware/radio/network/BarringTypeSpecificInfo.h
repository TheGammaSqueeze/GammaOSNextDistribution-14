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
namespace radio {
namespace network {
class BarringTypeSpecificInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t factor = 0;
  int32_t timeSeconds = 0;
  bool isBarred = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const BarringTypeSpecificInfo& rhs) const {
    return std::tie(factor, timeSeconds, isBarred) != std::tie(rhs.factor, rhs.timeSeconds, rhs.isBarred);
  }
  inline bool operator<(const BarringTypeSpecificInfo& rhs) const {
    return std::tie(factor, timeSeconds, isBarred) < std::tie(rhs.factor, rhs.timeSeconds, rhs.isBarred);
  }
  inline bool operator<=(const BarringTypeSpecificInfo& rhs) const {
    return std::tie(factor, timeSeconds, isBarred) <= std::tie(rhs.factor, rhs.timeSeconds, rhs.isBarred);
  }
  inline bool operator==(const BarringTypeSpecificInfo& rhs) const {
    return std::tie(factor, timeSeconds, isBarred) == std::tie(rhs.factor, rhs.timeSeconds, rhs.isBarred);
  }
  inline bool operator>(const BarringTypeSpecificInfo& rhs) const {
    return std::tie(factor, timeSeconds, isBarred) > std::tie(rhs.factor, rhs.timeSeconds, rhs.isBarred);
  }
  inline bool operator>=(const BarringTypeSpecificInfo& rhs) const {
    return std::tie(factor, timeSeconds, isBarred) >= std::tie(rhs.factor, rhs.timeSeconds, rhs.isBarred);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "BarringTypeSpecificInfo{";
    os << "factor: " << ::android::internal::ToString(factor);
    os << ", timeSeconds: " << ::android::internal::ToString(timeSeconds);
    os << ", isBarred: " << ::android::internal::ToString(isBarred);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
