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
enum class ControlExtendedSceneMode : int32_t {
  ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED = 0,
  ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE = 1,
  ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS = 2,
  ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START = 64,
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
[[nodiscard]] static inline std::string toString(ControlExtendedSceneMode val) {
  switch(val) {
  case ControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED:
    return "ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED";
  case ControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE:
    return "ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE";
  case ControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS:
    return "ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS";
  case ControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START:
    return "ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlExtendedSceneMode, 4> enum_values<aidl::android::hardware::camera::metadata::ControlExtendedSceneMode> = {
  aidl::android::hardware::camera::metadata::ControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED,
  aidl::android::hardware::camera::metadata::ControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE,
  aidl::android::hardware::camera::metadata::ControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS,
  aidl::android::hardware::camera::metadata::ControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
