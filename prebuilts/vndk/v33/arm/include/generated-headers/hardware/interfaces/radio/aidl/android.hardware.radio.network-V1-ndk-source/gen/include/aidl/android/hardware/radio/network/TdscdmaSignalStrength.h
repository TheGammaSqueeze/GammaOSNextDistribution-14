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
class TdscdmaSignalStrength {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t signalStrength = 0;
  int32_t bitErrorRate = 0;
  int32_t rscp = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const TdscdmaSignalStrength& rhs) const {
    return std::tie(signalStrength, bitErrorRate, rscp) != std::tie(rhs.signalStrength, rhs.bitErrorRate, rhs.rscp);
  }
  inline bool operator<(const TdscdmaSignalStrength& rhs) const {
    return std::tie(signalStrength, bitErrorRate, rscp) < std::tie(rhs.signalStrength, rhs.bitErrorRate, rhs.rscp);
  }
  inline bool operator<=(const TdscdmaSignalStrength& rhs) const {
    return std::tie(signalStrength, bitErrorRate, rscp) <= std::tie(rhs.signalStrength, rhs.bitErrorRate, rhs.rscp);
  }
  inline bool operator==(const TdscdmaSignalStrength& rhs) const {
    return std::tie(signalStrength, bitErrorRate, rscp) == std::tie(rhs.signalStrength, rhs.bitErrorRate, rhs.rscp);
  }
  inline bool operator>(const TdscdmaSignalStrength& rhs) const {
    return std::tie(signalStrength, bitErrorRate, rscp) > std::tie(rhs.signalStrength, rhs.bitErrorRate, rhs.rscp);
  }
  inline bool operator>=(const TdscdmaSignalStrength& rhs) const {
    return std::tie(signalStrength, bitErrorRate, rscp) >= std::tie(rhs.signalStrength, rhs.bitErrorRate, rhs.rscp);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "TdscdmaSignalStrength{";
    os << "signalStrength: " << ::android::internal::ToString(signalStrength);
    os << ", bitErrorRate: " << ::android::internal::ToString(bitErrorRate);
    os << ", rscp: " << ::android::internal::ToString(rscp);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
