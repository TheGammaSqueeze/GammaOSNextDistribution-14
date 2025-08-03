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
enum class DimmingStage : int32_t {
  NONE = 0,
  LINEAR = 1,
  GAMMA_OETF = 2,
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
[[nodiscard]] static inline std::string toString(DimmingStage val) {
  switch(val) {
  case DimmingStage::NONE:
    return "NONE";
  case DimmingStage::LINEAR:
    return "LINEAR";
  case DimmingStage::GAMMA_OETF:
    return "GAMMA_OETF";
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
constexpr inline std::array<aidl::android::hardware::graphics::composer3::DimmingStage, 3> enum_values<aidl::android::hardware::graphics::composer3::DimmingStage> = {
  aidl::android::hardware::graphics::composer3::DimmingStage::NONE,
  aidl::android::hardware::graphics::composer3::DimmingStage::LINEAR,
  aidl::android::hardware::graphics::composer3::DimmingStage::GAMMA_OETF,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
