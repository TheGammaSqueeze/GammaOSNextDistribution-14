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
enum class PowerMode : int32_t {
  OFF = 0,
  DOZE = 1,
  DOZE_SUSPEND = 3,
  ON = 2,
  ON_SUSPEND = 4,
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
[[nodiscard]] static inline std::string toString(PowerMode val) {
  switch(val) {
  case PowerMode::OFF:
    return "OFF";
  case PowerMode::DOZE:
    return "DOZE";
  case PowerMode::DOZE_SUSPEND:
    return "DOZE_SUSPEND";
  case PowerMode::ON:
    return "ON";
  case PowerMode::ON_SUSPEND:
    return "ON_SUSPEND";
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
constexpr inline std::array<aidl::android::hardware::graphics::composer3::PowerMode, 5> enum_values<aidl::android::hardware::graphics::composer3::PowerMode> = {
  aidl::android::hardware::graphics::composer3::PowerMode::OFF,
  aidl::android::hardware::graphics::composer3::PowerMode::DOZE,
  aidl::android::hardware::graphics::composer3::PowerMode::DOZE_SUSPEND,
  aidl::android::hardware::graphics::composer3::PowerMode::ON,
  aidl::android::hardware::graphics::composer3::PowerMode::ON_SUSPEND,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
