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
enum class FormatColorComponent : int8_t {
  FORMAT_COMPONENT_0 = 1,
  FORMAT_COMPONENT_1 = 2,
  FORMAT_COMPONENT_2 = 4,
  FORMAT_COMPONENT_3 = 8,
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
[[nodiscard]] static inline std::string toString(FormatColorComponent val) {
  switch(val) {
  case FormatColorComponent::FORMAT_COMPONENT_0:
    return "FORMAT_COMPONENT_0";
  case FormatColorComponent::FORMAT_COMPONENT_1:
    return "FORMAT_COMPONENT_1";
  case FormatColorComponent::FORMAT_COMPONENT_2:
    return "FORMAT_COMPONENT_2";
  case FormatColorComponent::FORMAT_COMPONENT_3:
    return "FORMAT_COMPONENT_3";
  default:
    return std::to_string(static_cast<int8_t>(val));
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
constexpr inline std::array<aidl::android::hardware::graphics::composer3::FormatColorComponent, 4> enum_values<aidl::android::hardware::graphics::composer3::FormatColorComponent> = {
  aidl::android::hardware::graphics::composer3::FormatColorComponent::FORMAT_COMPONENT_0,
  aidl::android::hardware::graphics::composer3::FormatColorComponent::FORMAT_COMPONENT_1,
  aidl::android::hardware::graphics::composer3::FormatColorComponent::FORMAT_COMPONENT_2,
  aidl::android::hardware::graphics::composer3::FormatColorComponent::FORMAT_COMPONENT_3,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
