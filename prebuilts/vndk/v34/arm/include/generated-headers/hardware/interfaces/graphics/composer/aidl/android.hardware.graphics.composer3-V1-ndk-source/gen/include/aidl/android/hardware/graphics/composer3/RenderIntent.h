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
enum class RenderIntent : int32_t {
  COLORIMETRIC = 0,
  ENHANCE = 1,
  TONE_MAP_COLORIMETRIC = 2,
  TONE_MAP_ENHANCE = 3,
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
[[nodiscard]] static inline std::string toString(RenderIntent val) {
  switch(val) {
  case RenderIntent::COLORIMETRIC:
    return "COLORIMETRIC";
  case RenderIntent::ENHANCE:
    return "ENHANCE";
  case RenderIntent::TONE_MAP_COLORIMETRIC:
    return "TONE_MAP_COLORIMETRIC";
  case RenderIntent::TONE_MAP_ENHANCE:
    return "TONE_MAP_ENHANCE";
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
constexpr inline std::array<aidl::android::hardware::graphics::composer3::RenderIntent, 4> enum_values<aidl::android::hardware::graphics::composer3::RenderIntent> = {
  aidl::android::hardware::graphics::composer3::RenderIntent::COLORIMETRIC,
  aidl::android::hardware::graphics::composer3::RenderIntent::ENHANCE,
  aidl::android::hardware::graphics::composer3::RenderIntent::TONE_MAP_COLORIMETRIC,
  aidl::android::hardware::graphics::composer3::RenderIntent::TONE_MAP_ENHANCE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
