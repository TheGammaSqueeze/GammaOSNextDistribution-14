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
enum class ParamSizeLimits : int32_t {
  SSID_MAX_LEN_IN_BYTES = 32,
  WPA2_PSK_PASSPHRASE_MIN_LEN_IN_BYTES = 8,
  WPA2_PSK_PASSPHRASE_MAX_LEN_IN_BYTES = 63,
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
[[nodiscard]] static inline std::string toString(ParamSizeLimits val) {
  switch(val) {
  case ParamSizeLimits::SSID_MAX_LEN_IN_BYTES:
    return "SSID_MAX_LEN_IN_BYTES";
  case ParamSizeLimits::WPA2_PSK_PASSPHRASE_MIN_LEN_IN_BYTES:
    return "WPA2_PSK_PASSPHRASE_MIN_LEN_IN_BYTES";
  case ParamSizeLimits::WPA2_PSK_PASSPHRASE_MAX_LEN_IN_BYTES:
    return "WPA2_PSK_PASSPHRASE_MAX_LEN_IN_BYTES";
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
constexpr inline std::array<aidl::android::hardware::wifi::hostapd::ParamSizeLimits, 3> enum_values<aidl::android::hardware::wifi::hostapd::ParamSizeLimits> = {
  aidl::android::hardware::wifi::hostapd::ParamSizeLimits::SSID_MAX_LEN_IN_BYTES,
  aidl::android::hardware::wifi::hostapd::ParamSizeLimits::WPA2_PSK_PASSPHRASE_MIN_LEN_IN_BYTES,
  aidl::android::hardware::wifi::hostapd::ParamSizeLimits::WPA2_PSK_PASSPHRASE_MAX_LEN_IN_BYTES,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
