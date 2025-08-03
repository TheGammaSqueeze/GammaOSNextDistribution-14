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
namespace voice {
class EmergencyNumber {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string number;
  std::string mcc;
  std::string mnc;
  int32_t categories = 0;
  std::vector<std::string> urns;
  int32_t sources = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const EmergencyNumber& rhs) const {
    return std::tie(number, mcc, mnc, categories, urns, sources) != std::tie(rhs.number, rhs.mcc, rhs.mnc, rhs.categories, rhs.urns, rhs.sources);
  }
  inline bool operator<(const EmergencyNumber& rhs) const {
    return std::tie(number, mcc, mnc, categories, urns, sources) < std::tie(rhs.number, rhs.mcc, rhs.mnc, rhs.categories, rhs.urns, rhs.sources);
  }
  inline bool operator<=(const EmergencyNumber& rhs) const {
    return std::tie(number, mcc, mnc, categories, urns, sources) <= std::tie(rhs.number, rhs.mcc, rhs.mnc, rhs.categories, rhs.urns, rhs.sources);
  }
  inline bool operator==(const EmergencyNumber& rhs) const {
    return std::tie(number, mcc, mnc, categories, urns, sources) == std::tie(rhs.number, rhs.mcc, rhs.mnc, rhs.categories, rhs.urns, rhs.sources);
  }
  inline bool operator>(const EmergencyNumber& rhs) const {
    return std::tie(number, mcc, mnc, categories, urns, sources) > std::tie(rhs.number, rhs.mcc, rhs.mnc, rhs.categories, rhs.urns, rhs.sources);
  }
  inline bool operator>=(const EmergencyNumber& rhs) const {
    return std::tie(number, mcc, mnc, categories, urns, sources) >= std::tie(rhs.number, rhs.mcc, rhs.mnc, rhs.categories, rhs.urns, rhs.sources);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { SOURCE_NETWORK_SIGNALING = 1 };
  enum : int32_t { SOURCE_SIM = 2 };
  enum : int32_t { SOURCE_MODEM_CONFIG = 4 };
  enum : int32_t { SOURCE_DEFAULT = 8 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "EmergencyNumber{";
    os << "number: " << ::android::internal::ToString(number);
    os << ", mcc: " << ::android::internal::ToString(mcc);
    os << ", mnc: " << ::android::internal::ToString(mnc);
    os << ", categories: " << ::android::internal::ToString(categories);
    os << ", urns: " << ::android::internal::ToString(urns);
    os << ", sources: " << ::android::internal::ToString(sources);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
