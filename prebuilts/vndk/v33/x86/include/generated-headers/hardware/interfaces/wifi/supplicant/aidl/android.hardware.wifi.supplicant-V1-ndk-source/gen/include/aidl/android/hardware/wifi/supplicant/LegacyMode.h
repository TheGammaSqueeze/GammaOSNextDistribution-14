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
enum class LegacyMode : int32_t {
  UNKNOWN = 0,
  A_MODE = 1,
  B_MODE = 2,
  G_MODE = 3,
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
[[nodiscard]] static inline std::string toString(LegacyMode val) {
  switch(val) {
  case LegacyMode::UNKNOWN:
    return "UNKNOWN";
  case LegacyMode::A_MODE:
    return "A_MODE";
  case LegacyMode::B_MODE:
    return "B_MODE";
  case LegacyMode::G_MODE:
    return "G_MODE";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::LegacyMode, 4> enum_values<aidl::android::hardware::wifi::supplicant::LegacyMode> = {
  aidl::android::hardware::wifi::supplicant::LegacyMode::UNKNOWN,
  aidl::android::hardware::wifi::supplicant::LegacyMode::A_MODE,
  aidl::android::hardware::wifi::supplicant::LegacyMode::B_MODE,
  aidl::android::hardware::wifi::supplicant::LegacyMode::G_MODE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
