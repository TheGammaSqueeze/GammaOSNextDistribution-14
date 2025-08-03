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
enum class DppNetRole : int32_t {
  STA = 0,
  AP = 1,
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
[[nodiscard]] static inline std::string toString(DppNetRole val) {
  switch(val) {
  case DppNetRole::STA:
    return "STA";
  case DppNetRole::AP:
    return "AP";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::DppNetRole, 2> enum_values<aidl::android::hardware::wifi::supplicant::DppNetRole> = {
  aidl::android::hardware::wifi::supplicant::DppNetRole::STA,
  aidl::android::hardware::wifi::supplicant::DppNetRole::AP,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
