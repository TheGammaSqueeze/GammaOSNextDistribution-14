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
enum class ControlMode : int32_t {
  ANDROID_CONTROL_MODE_OFF = 0,
  ANDROID_CONTROL_MODE_AUTO = 1,
  ANDROID_CONTROL_MODE_USE_SCENE_MODE = 2,
  ANDROID_CONTROL_MODE_OFF_KEEP_STATE = 3,
  ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE = 4,
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
[[nodiscard]] static inline std::string toString(ControlMode val) {
  switch(val) {
  case ControlMode::ANDROID_CONTROL_MODE_OFF:
    return "ANDROID_CONTROL_MODE_OFF";
  case ControlMode::ANDROID_CONTROL_MODE_AUTO:
    return "ANDROID_CONTROL_MODE_AUTO";
  case ControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE:
    return "ANDROID_CONTROL_MODE_USE_SCENE_MODE";
  case ControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE:
    return "ANDROID_CONTROL_MODE_OFF_KEEP_STATE";
  case ControlMode::ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE:
    return "ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlMode, 5> enum_values<aidl::android::hardware::camera::metadata::ControlMode> = {
  aidl::android::hardware::camera::metadata::ControlMode::ANDROID_CONTROL_MODE_OFF,
  aidl::android::hardware::camera::metadata::ControlMode::ANDROID_CONTROL_MODE_AUTO,
  aidl::android::hardware::camera::metadata::ControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE,
  aidl::android::hardware::camera::metadata::ControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE,
  aidl::android::hardware::camera::metadata::ControlMode::ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
