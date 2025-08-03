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
namespace sim {
class IccIoResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t sw1 = 0;
  int32_t sw2 = 0;
  std::string simResponse;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const IccIoResult& rhs) const {
    return std::tie(sw1, sw2, simResponse) != std::tie(rhs.sw1, rhs.sw2, rhs.simResponse);
  }
  inline bool operator<(const IccIoResult& rhs) const {
    return std::tie(sw1, sw2, simResponse) < std::tie(rhs.sw1, rhs.sw2, rhs.simResponse);
  }
  inline bool operator<=(const IccIoResult& rhs) const {
    return std::tie(sw1, sw2, simResponse) <= std::tie(rhs.sw1, rhs.sw2, rhs.simResponse);
  }
  inline bool operator==(const IccIoResult& rhs) const {
    return std::tie(sw1, sw2, simResponse) == std::tie(rhs.sw1, rhs.sw2, rhs.simResponse);
  }
  inline bool operator>(const IccIoResult& rhs) const {
    return std::tie(sw1, sw2, simResponse) > std::tie(rhs.sw1, rhs.sw2, rhs.simResponse);
  }
  inline bool operator>=(const IccIoResult& rhs) const {
    return std::tie(sw1, sw2, simResponse) >= std::tie(rhs.sw1, rhs.sw2, rhs.simResponse);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "IccIoResult{";
    os << "sw1: " << ::android::internal::ToString(sw1);
    os << ", sw2: " << ::android::internal::ToString(sw2);
    os << ", simResponse: " << ::android::internal::ToString(simResponse);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
