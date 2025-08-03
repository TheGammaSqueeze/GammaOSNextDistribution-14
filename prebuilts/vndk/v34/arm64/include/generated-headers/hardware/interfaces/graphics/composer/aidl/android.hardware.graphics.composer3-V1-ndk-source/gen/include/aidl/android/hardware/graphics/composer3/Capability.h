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
namespace graphics {
namespace composer3 {
enum class Capability : int32_t {
  INVALID = 0,
  SIDEBAND_STREAM = 1,
  SKIP_CLIENT_COLOR_TRANSFORM = 2,
  PRESENT_FENCE_IS_NOT_RELIABLE = 3,
  SKIP_VALIDATE = 4,
  BOOT_DISPLAY_CONFIG = 5,
};

}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
[[nodiscard]] static inline std::string toString(Capability val) {
  switch(val) {
  case Capability::INVALID:
    return "INVALID";
  case Capability::SIDEBAND_STREAM:
    return "SIDEBAND_STREAM";
  case Capability::SKIP_CLIENT_COLOR_TRANSFORM:
    return "SKIP_CLIENT_COLOR_TRANSFORM";
  case Capability::PRESENT_FENCE_IS_NOT_RELIABLE:
    return "PRESENT_FENCE_IS_NOT_RELIABLE";
  case Capability::SKIP_VALIDATE:
    return "SKIP_VALIDATE";
  case Capability::BOOT_DISPLAY_CONFIG:
    return "BOOT_DISPLAY_CONFIG";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::graphics::composer3::Capability, 6> enum_values<aidl::android::hardware::graphics::composer3::Capability> = {
  aidl::android::hardware::graphics::composer3::Capability::INVALID,
  aidl::android::hardware::graphics::composer3::Capability::SIDEBAND_STREAM,
  aidl::android::hardware::graphics::composer3::Capability::SKIP_CLIENT_COLOR_TRANSFORM,
  aidl::android::hardware::graphics::composer3::Capability::PRESENT_FENCE_IS_NOT_RELIABLE,
  aidl::android::hardware::graphics::composer3::Capability::SKIP_VALIDATE,
  aidl::android::hardware::graphics::composer3::Capability::BOOT_DISPLAY_CONFIG,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
