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
enum class Generation : int32_t {
  WIFI_STANDARD_UNKNOWN = -1,
  WIFI_STANDARD_LEGACY = 0,
  WIFI_STANDARD_11N = 1,
  WIFI_STANDARD_11AC = 2,
  WIFI_STANDARD_11AD = 3,
  WIFI_STANDARD_11AX = 4,
  WIFI_STANDARD_11BE = 5,
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
[[nodiscard]] static inline std::string toString(Generation val) {
  switch(val) {
  case Generation::WIFI_STANDARD_UNKNOWN:
    return "WIFI_STANDARD_UNKNOWN";
  case Generation::WIFI_STANDARD_LEGACY:
    return "WIFI_STANDARD_LEGACY";
  case Generation::WIFI_STANDARD_11N:
    return "WIFI_STANDARD_11N";
  case Generation::WIFI_STANDARD_11AC:
    return "WIFI_STANDARD_11AC";
  case Generation::WIFI_STANDARD_11AD:
    return "WIFI_STANDARD_11AD";
  case Generation::WIFI_STANDARD_11AX:
    return "WIFI_STANDARD_11AX";
  case Generation::WIFI_STANDARD_11BE:
    return "WIFI_STANDARD_11BE";
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
constexpr inline std::array<aidl::android::hardware::wifi::hostapd::Generation, 7> enum_values<aidl::android::hardware::wifi::hostapd::Generation> = {
  aidl::android::hardware::wifi::hostapd::Generation::WIFI_STANDARD_UNKNOWN,
  aidl::android::hardware::wifi::hostapd::Generation::WIFI_STANDARD_LEGACY,
  aidl::android::hardware::wifi::hostapd::Generation::WIFI_STANDARD_11N,
  aidl::android::hardware::wifi::hostapd::Generation::WIFI_STANDARD_11AC,
  aidl::android::hardware::wifi::hostapd::Generation::WIFI_STANDARD_11AD,
  aidl::android::hardware::wifi::hostapd::Generation::WIFI_STANDARD_11AX,
  aidl::android::hardware::wifi::hostapd::Generation::WIFI_STANDARD_11BE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
