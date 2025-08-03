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
enum class WpsProvisionMethod : int32_t {
  PBC = 0,
  DISPLAY = 1,
  KEYPAD = 2,
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
[[nodiscard]] static inline std::string toString(WpsProvisionMethod val) {
  switch(val) {
  case WpsProvisionMethod::PBC:
    return "PBC";
  case WpsProvisionMethod::DISPLAY:
    return "DISPLAY";
  case WpsProvisionMethod::KEYPAD:
    return "KEYPAD";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::WpsProvisionMethod, 3> enum_values<aidl::android::hardware::wifi::supplicant::WpsProvisionMethod> = {
  aidl::android::hardware::wifi::supplicant::WpsProvisionMethod::PBC,
  aidl::android::hardware::wifi::supplicant::WpsProvisionMethod::DISPLAY,
  aidl::android::hardware::wifi::supplicant::WpsProvisionMethod::KEYPAD,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
