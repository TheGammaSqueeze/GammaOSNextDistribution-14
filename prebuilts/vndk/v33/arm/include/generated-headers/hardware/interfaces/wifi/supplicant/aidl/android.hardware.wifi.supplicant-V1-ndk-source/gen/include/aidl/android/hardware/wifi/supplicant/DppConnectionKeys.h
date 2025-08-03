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
class DppConnectionKeys {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> connector;
  std::vector<uint8_t> cSign;
  std::vector<uint8_t> netAccessKey;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DppConnectionKeys& rhs) const {
    return std::tie(connector, cSign, netAccessKey) != std::tie(rhs.connector, rhs.cSign, rhs.netAccessKey);
  }
  inline bool operator<(const DppConnectionKeys& rhs) const {
    return std::tie(connector, cSign, netAccessKey) < std::tie(rhs.connector, rhs.cSign, rhs.netAccessKey);
  }
  inline bool operator<=(const DppConnectionKeys& rhs) const {
    return std::tie(connector, cSign, netAccessKey) <= std::tie(rhs.connector, rhs.cSign, rhs.netAccessKey);
  }
  inline bool operator==(const DppConnectionKeys& rhs) const {
    return std::tie(connector, cSign, netAccessKey) == std::tie(rhs.connector, rhs.cSign, rhs.netAccessKey);
  }
  inline bool operator>(const DppConnectionKeys& rhs) const {
    return std::tie(connector, cSign, netAccessKey) > std::tie(rhs.connector, rhs.cSign, rhs.netAccessKey);
  }
  inline bool operator>=(const DppConnectionKeys& rhs) const {
    return std::tie(connector, cSign, netAccessKey) >= std::tie(rhs.connector, rhs.cSign, rhs.netAccessKey);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DppConnectionKeys{";
    os << "connector: " << ::android::internal::ToString(connector);
    os << ", cSign: " << ::android::internal::ToString(cSign);
    os << ", netAccessKey: " << ::android::internal::ToString(netAccessKey);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
