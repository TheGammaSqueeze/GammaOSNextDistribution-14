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
enum class WpaDriverCapabilitiesMask : int32_t {
  MBO = 1,
  OCE = 2,
  SAE_PK = 4,
  WFD_R2 = 8,
  TRUST_ON_FIRST_USE = 16,
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
[[nodiscard]] static inline std::string toString(WpaDriverCapabilitiesMask val) {
  switch(val) {
  case WpaDriverCapabilitiesMask::MBO:
    return "MBO";
  case WpaDriverCapabilitiesMask::OCE:
    return "OCE";
  case WpaDriverCapabilitiesMask::SAE_PK:
    return "SAE_PK";
  case WpaDriverCapabilitiesMask::WFD_R2:
    return "WFD_R2";
  case WpaDriverCapabilitiesMask::TRUST_ON_FIRST_USE:
    return "TRUST_ON_FIRST_USE";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::WpaDriverCapabilitiesMask, 5> enum_values<aidl::android::hardware::wifi::supplicant::WpaDriverCapabilitiesMask> = {
  aidl::android::hardware::wifi::supplicant::WpaDriverCapabilitiesMask::MBO,
  aidl::android::hardware::wifi::supplicant::WpaDriverCapabilitiesMask::OCE,
  aidl::android::hardware::wifi::supplicant::WpaDriverCapabilitiesMask::SAE_PK,
  aidl::android::hardware::wifi::supplicant::WpaDriverCapabilitiesMask::WFD_R2,
  aidl::android::hardware::wifi::supplicant::WpaDriverCapabilitiesMask::TRUST_ON_FIRST_USE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
