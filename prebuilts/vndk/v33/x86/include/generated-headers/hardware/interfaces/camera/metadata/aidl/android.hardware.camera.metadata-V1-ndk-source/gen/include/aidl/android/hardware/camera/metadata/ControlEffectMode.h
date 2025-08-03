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
namespace camera {
namespace metadata {
enum class ControlEffectMode : int32_t {
  ANDROID_CONTROL_EFFECT_MODE_OFF = 0,
  ANDROID_CONTROL_EFFECT_MODE_MONO = 1,
  ANDROID_CONTROL_EFFECT_MODE_NEGATIVE = 2,
  ANDROID_CONTROL_EFFECT_MODE_SOLARIZE = 3,
  ANDROID_CONTROL_EFFECT_MODE_SEPIA = 4,
  ANDROID_CONTROL_EFFECT_MODE_POSTERIZE = 5,
  ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD = 6,
  ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD = 7,
  ANDROID_CONTROL_EFFECT_MODE_AQUA = 8,
};

}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace metadata {
[[nodiscard]] static inline std::string toString(ControlEffectMode val) {
  switch(val) {
  case ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_OFF:
    return "ANDROID_CONTROL_EFFECT_MODE_OFF";
  case ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_MONO:
    return "ANDROID_CONTROL_EFFECT_MODE_MONO";
  case ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_NEGATIVE:
    return "ANDROID_CONTROL_EFFECT_MODE_NEGATIVE";
  case ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SOLARIZE:
    return "ANDROID_CONTROL_EFFECT_MODE_SOLARIZE";
  case ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SEPIA:
    return "ANDROID_CONTROL_EFFECT_MODE_SEPIA";
  case ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_POSTERIZE:
    return "ANDROID_CONTROL_EFFECT_MODE_POSTERIZE";
  case ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD:
    return "ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD";
  case ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD:
    return "ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD";
  case ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_AQUA:
    return "ANDROID_CONTROL_EFFECT_MODE_AQUA";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlEffectMode, 9> enum_values<aidl::android::hardware::camera::metadata::ControlEffectMode> = {
  aidl::android::hardware::camera::metadata::ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_OFF,
  aidl::android::hardware::camera::metadata::ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_MONO,
  aidl::android::hardware::camera::metadata::ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_NEGATIVE,
  aidl::android::hardware::camera::metadata::ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SOLARIZE,
  aidl::android::hardware::camera::metadata::ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SEPIA,
  aidl::android::hardware::camera::metadata::ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_POSTERIZE,
  aidl::android::hardware::camera::metadata::ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD,
  aidl::android::hardware::camera::metadata::ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD,
  aidl::android::hardware::camera::metadata::ControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_AQUA,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
