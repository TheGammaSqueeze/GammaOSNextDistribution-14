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
enum class DisplayConnectionType : int32_t {
  INTERNAL = 0,
  EXTERNAL = 1,
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
[[nodiscard]] static inline std::string toString(DisplayConnectionType val) {
  switch(val) {
  case DisplayConnectionType::INTERNAL:
    return "INTERNAL";
  case DisplayConnectionType::EXTERNAL:
    return "EXTERNAL";
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
constexpr inline std::array<aidl::android::hardware::graphics::composer3::DisplayConnectionType, 2> enum_values<aidl::android::hardware::graphics::composer3::DisplayConnectionType> = {
  aidl::android::hardware::graphics::composer3::DisplayConnectionType::INTERNAL,
  aidl::android::hardware::graphics::composer3::DisplayConnectionType::EXTERNAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
