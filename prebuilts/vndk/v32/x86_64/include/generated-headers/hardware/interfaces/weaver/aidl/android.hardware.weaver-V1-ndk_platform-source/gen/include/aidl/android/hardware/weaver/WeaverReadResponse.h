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
namespace weaver {
class WeaverReadResponse {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t timeout = 0L;
  std::vector<uint8_t> value;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const WeaverReadResponse& rhs) const {
    return std::tie(timeout, value) != std::tie(rhs.timeout, rhs.value);
  }
  inline bool operator<(const WeaverReadResponse& rhs) const {
    return std::tie(timeout, value) < std::tie(rhs.timeout, rhs.value);
  }
  inline bool operator<=(const WeaverReadResponse& rhs) const {
    return std::tie(timeout, value) <= std::tie(rhs.timeout, rhs.value);
  }
  inline bool operator==(const WeaverReadResponse& rhs) const {
    return std::tie(timeout, value) == std::tie(rhs.timeout, rhs.value);
  }
  inline bool operator>(const WeaverReadResponse& rhs) const {
    return std::tie(timeout, value) > std::tie(rhs.timeout, rhs.value);
  }
  inline bool operator>=(const WeaverReadResponse& rhs) const {
    return std::tie(timeout, value) >= std::tie(rhs.timeout, rhs.value);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "WeaverReadResponse{";
    os << "timeout: " << ::android::internal::ToString(timeout);
    os << ", value: " << ::android::internal::ToString(value);
    os << "}";
    return os.str();
  }
};
}  // namespace weaver
}  // namespace hardware
}  // namespace android
}  // namespace aidl
