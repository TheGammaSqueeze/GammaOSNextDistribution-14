#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
enum class Ieee80211ReasonCode : int32_t {
  WLAN_REASON_UNSPECIFIED = 1,
  WLAN_REASON_PREV_AUTH_NOT_VALID = 2,
  WLAN_REASON_DISASSOC_AP_BUSY = 5,
};

}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
[[nodiscard]] static inline std::string toString(Ieee80211ReasonCode val) {
  switch(val) {
  case Ieee80211ReasonCode::WLAN_REASON_UNSPECIFIED:
    return "WLAN_REASON_UNSPECIFIED";
  case Ieee80211ReasonCode::WLAN_REASON_PREV_AUTH_NOT_VALID:
    return "WLAN_REASON_PREV_AUTH_NOT_VALID";
  case Ieee80211ReasonCode::WLAN_REASON_DISASSOC_AP_BUSY:
    return "WLAN_REASON_DISASSOC_AP_BUSY";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::wifi::hostapd::Ieee80211ReasonCode, 3> enum_values<aidl::android::hardware::wifi::hostapd::Ieee80211ReasonCode> = {
  aidl::android::hardware::wifi::hostapd::Ieee80211ReasonCode::WLAN_REASON_UNSPECIFIED,
  aidl::android::hardware::wifi::hostapd::Ieee80211ReasonCode::WLAN_REASON_PREV_AUTH_NOT_VALID,
  aidl::android::hardware::wifi::hostapd::Ieee80211ReasonCode::WLAN_REASON_DISASSOC_AP_BUSY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
