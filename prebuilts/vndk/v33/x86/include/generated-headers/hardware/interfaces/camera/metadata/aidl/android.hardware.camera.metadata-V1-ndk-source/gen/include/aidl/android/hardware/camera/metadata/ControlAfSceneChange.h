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
enum class ControlAfSceneChange : int32_t {
  ANDROID_CONTROL_AF_SCENE_CHANGE_NOT_DETECTED = 0,
  ANDROID_CONTROL_AF_SCENE_CHANGE_DETECTED = 1,
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
[[nodiscard]] static inline std::string toString(ControlAfSceneChange val) {
  switch(val) {
  case ControlAfSceneChange::ANDROID_CONTROL_AF_SCENE_CHANGE_NOT_DETECTED:
    return "ANDROID_CONTROL_AF_SCENE_CHANGE_NOT_DETECTED";
  case ControlAfSceneChange::ANDROID_CONTROL_AF_SCENE_CHANGE_DETECTED:
    return "ANDROID_CONTROL_AF_SCENE_CHANGE_DETECTED";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlAfSceneChange, 2> enum_values<aidl::android::hardware::camera::metadata::ControlAfSceneChange> = {
  aidl::android::hardware::camera::metadata::ControlAfSceneChange::ANDROID_CONTROL_AF_SCENE_CHANGE_NOT_DETECTED,
  aidl::android::hardware::camera::metadata::ControlAfSceneChange::ANDROID_CONTROL_AF_SCENE_CHANGE_DETECTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
