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
class NetworkRequestEapSimUmtsAuthParams {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> rand;
  std::vector<uint8_t> autn;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NetworkRequestEapSimUmtsAuthParams& rhs) const {
    return std::tie(rand, autn) != std::tie(rhs.rand, rhs.autn);
  }
  inline bool operator<(const NetworkRequestEapSimUmtsAuthParams& rhs) const {
    return std::tie(rand, autn) < std::tie(rhs.rand, rhs.autn);
  }
  inline bool operator<=(const NetworkRequestEapSimUmtsAuthParams& rhs) const {
    return std::tie(rand, autn) <= std::tie(rhs.rand, rhs.autn);
  }
  inline bool operator==(const NetworkRequestEapSimUmtsAuthParams& rhs) const {
    return std::tie(rand, autn) == std::tie(rhs.rand, rhs.autn);
  }
  inline bool operator>(const NetworkRequestEapSimUmtsAuthParams& rhs) const {
    return std::tie(rand, autn) > std::tie(rhs.rand, rhs.autn);
  }
  inline bool operator>=(const NetworkRequestEapSimUmtsAuthParams& rhs) const {
    return std::tie(rand, autn) >= std::tie(rhs.rand, rhs.autn);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "NetworkRequestEapSimUmtsAuthParams{";
    os << "rand: " << ::android::internal::ToString(rand);
    os << ", autn: " << ::android::internal::ToString(autn);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
