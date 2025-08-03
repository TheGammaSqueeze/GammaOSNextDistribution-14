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
enum class AuthAlgMask : int32_t {
  OPEN = 1,
  SHARED = 2,
  LEAP = 4,
  SAE = 16,
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
[[nodiscard]] static inline std::string toString(AuthAlgMask val) {
  switch(val) {
  case AuthAlgMask::OPEN:
    return "OPEN";
  case AuthAlgMask::SHARED:
    return "SHARED";
  case AuthAlgMask::LEAP:
    return "LEAP";
  case AuthAlgMask::SAE:
    return "SAE";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::AuthAlgMask, 4> enum_values<aidl::android::hardware::wifi::supplicant::AuthAlgMask> = {
  aidl::android::hardware::wifi::supplicant::AuthAlgMask::OPEN,
  aidl::android::hardware::wifi::supplicant::AuthAlgMask::SHARED,
  aidl::android::hardware::wifi::supplicant::AuthAlgMask::LEAP,
  aidl::android::hardware::wifi::supplicant::AuthAlgMask::SAE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
