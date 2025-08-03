#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/hostapd/ChannelParams.h>
#include <aidl/android/hardware/wifi/hostapd/HwModeParams.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
class IfaceParams {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string name;
  ::aidl::android::hardware::wifi::hostapd::HwModeParams hwModeParams;
  std::vector<::aidl::android::hardware::wifi::hostapd::ChannelParams> channelParams;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const IfaceParams& rhs) const {
    return std::tie(name, hwModeParams, channelParams) != std::tie(rhs.name, rhs.hwModeParams, rhs.channelParams);
  }
  inline bool operator<(const IfaceParams& rhs) const {
    return std::tie(name, hwModeParams, channelParams) < std::tie(rhs.name, rhs.hwModeParams, rhs.channelParams);
  }
  inline bool operator<=(const IfaceParams& rhs) const {
    return std::tie(name, hwModeParams, channelParams) <= std::tie(rhs.name, rhs.hwModeParams, rhs.channelParams);
  }
  inline bool operator==(const IfaceParams& rhs) const {
    return std::tie(name, hwModeParams, channelParams) == std::tie(rhs.name, rhs.hwModeParams, rhs.channelParams);
  }
  inline bool operator>(const IfaceParams& rhs) const {
    return std::tie(name, hwModeParams, channelParams) > std::tie(rhs.name, rhs.hwModeParams, rhs.channelParams);
  }
  inline bool operator>=(const IfaceParams& rhs) const {
    return std::tie(name, hwModeParams, channelParams) >= std::tie(rhs.name, rhs.hwModeParams, rhs.channelParams);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "IfaceParams{";
    os << "name: " << ::android::internal::ToString(name);
    os << ", hwModeParams: " << ::android::internal::ToString(hwModeParams);
    os << ", channelParams: " << ::android::internal::ToString(channelParams);
    os << "}";
    return os.str();
  }
};
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
