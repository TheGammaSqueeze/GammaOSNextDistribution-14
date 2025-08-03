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
class NetworkResponseEapSimUmtsAuthParams {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> res;
  std::vector<uint8_t> ik;
  std::vector<uint8_t> ck;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NetworkResponseEapSimUmtsAuthParams& rhs) const {
    return std::tie(res, ik, ck) != std::tie(rhs.res, rhs.ik, rhs.ck);
  }
  inline bool operator<(const NetworkResponseEapSimUmtsAuthParams& rhs) const {
    return std::tie(res, ik, ck) < std::tie(rhs.res, rhs.ik, rhs.ck);
  }
  inline bool operator<=(const NetworkResponseEapSimUmtsAuthParams& rhs) const {
    return std::tie(res, ik, ck) <= std::tie(rhs.res, rhs.ik, rhs.ck);
  }
  inline bool operator==(const NetworkResponseEapSimUmtsAuthParams& rhs) const {
    return std::tie(res, ik, ck) == std::tie(rhs.res, rhs.ik, rhs.ck);
  }
  inline bool operator>(const NetworkResponseEapSimUmtsAuthParams& rhs) const {
    return std::tie(res, ik, ck) > std::tie(rhs.res, rhs.ik, rhs.ck);
  }
  inline bool operator>=(const NetworkResponseEapSimUmtsAuthParams& rhs) const {
    return std::tie(res, ik, ck) >= std::tie(rhs.res, rhs.ik, rhs.ck);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "NetworkResponseEapSimUmtsAuthParams{";
    os << "res: " << ::android::internal::ToString(res);
    os << ", ik: " << ::android::internal::ToString(ik);
    os << ", ck: " << ::android::internal::ToString(ck);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
