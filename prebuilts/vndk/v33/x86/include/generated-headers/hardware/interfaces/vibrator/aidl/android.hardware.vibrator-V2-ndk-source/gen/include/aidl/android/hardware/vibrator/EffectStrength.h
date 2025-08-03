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
namespace vibrator {
enum class EffectStrength : int8_t {
  LIGHT = 0,
  MEDIUM = 1,
  STRONG = 2,
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
[[nodiscard]] static inline std::string toString(EffectStrength val) {
  switch(val) {
  case EffectStrength::LIGHT:
    return "LIGHT";
  case EffectStrength::MEDIUM:
    return "MEDIUM";
  case EffectStrength::STRONG:
    return "STRONG";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::vibrator::EffectStrength, 3> enum_values<aidl::android::hardware::vibrator::EffectStrength> = {
  aidl::android::hardware::vibrator::EffectStrength::LIGHT,
  aidl::android::hardware::vibrator::EffectStrength::MEDIUM,
  aidl::android::hardware::vibrator::EffectStrength::STRONG,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
