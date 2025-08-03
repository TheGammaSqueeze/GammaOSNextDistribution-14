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
namespace supplicant {
enum class WifiTechnology : int32_t {
  UNKNOWN = 0,
  LEGACY = 1,
  HT = 2,
  VHT = 3,
  HE = 4,
  EHT = 5,
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
[[nodiscard]] static inline std::string toString(WifiTechnology val) {
  switch(val) {
  case WifiTechnology::UNKNOWN:
    return "UNKNOWN";
  case WifiTechnology::LEGACY:
    return "LEGACY";
  case WifiTechnology::HT:
    return "HT";
  case WifiTechnology::VHT:
    return "VHT";
  case WifiTechnology::HE:
    return "HE";
  case WifiTechnology::EHT:
    return "EHT";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::WifiTechnology, 6> enum_values<aidl::android::hardware::wifi::supplicant::WifiTechnology> = {
  aidl::android::hardware::wifi::supplicant::WifiTechnology::UNKNOWN,
  aidl::android::hardware::wifi::supplicant::WifiTechnology::LEGACY,
  aidl::android::hardware::wifi::supplicant::WifiTechnology::HT,
  aidl::android::hardware::wifi::supplicant::WifiTechnology::VHT,
  aidl::android::hardware::wifi::supplicant::WifiTechnology::HE,
  aidl::android::hardware::wifi::supplicant::WifiTechnology::EHT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
