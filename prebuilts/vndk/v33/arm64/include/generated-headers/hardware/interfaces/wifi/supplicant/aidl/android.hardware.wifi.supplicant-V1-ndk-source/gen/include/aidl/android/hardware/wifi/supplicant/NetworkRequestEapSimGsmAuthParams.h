#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/supplicant/GsmRand.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class NetworkRequestEapSimGsmAuthParams {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::wifi::supplicant::GsmRand> rands;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NetworkRequestEapSimGsmAuthParams& rhs) const {
    return std::tie(rands) != std::tie(rhs.rands);
  }
  inline bool operator<(const NetworkRequestEapSimGsmAuthParams& rhs) const {
    return std::tie(rands) < std::tie(rhs.rands);
  }
  inline bool operator<=(const NetworkRequestEapSimGsmAuthParams& rhs) const {
    return std::tie(rands) <= std::tie(rhs.rands);
  }
  inline bool operator==(const NetworkRequestEapSimGsmAuthParams& rhs) const {
    return std::tie(rands) == std::tie(rhs.rands);
  }
  inline bool operator>(const NetworkRequestEapSimGsmAuthParams& rhs) const {
    return std::tie(rands) > std::tie(rhs.rands);
  }
  inline bool operator>=(const NetworkRequestEapSimGsmAuthParams& rhs) const {
    return std::tie(rands) >= std::tie(rhs.rands);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "NetworkRequestEapSimGsmAuthParams{";
    os << "rands: " << ::android::internal::ToString(rands);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
