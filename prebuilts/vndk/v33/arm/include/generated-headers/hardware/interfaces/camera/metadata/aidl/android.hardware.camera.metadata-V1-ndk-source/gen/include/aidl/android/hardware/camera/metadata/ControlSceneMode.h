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
enum class ControlSceneMode : int32_t {
  ANDROID_CONTROL_SCENE_MODE_DISABLED = 0,
  ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY = 1,
  ANDROID_CONTROL_SCENE_MODE_ACTION = 2,
  ANDROID_CONTROL_SCENE_MODE_PORTRAIT = 3,
  ANDROID_CONTROL_SCENE_MODE_LANDSCAPE = 4,
  ANDROID_CONTROL_SCENE_MODE_NIGHT = 5,
  ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT = 6,
  ANDROID_CONTROL_SCENE_MODE_THEATRE = 7,
  ANDROID_CONTROL_SCENE_MODE_BEACH = 8,
  ANDROID_CONTROL_SCENE_MODE_SNOW = 9,
  ANDROID_CONTROL_SCENE_MODE_SUNSET = 10,
  ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO = 11,
  ANDROID_CONTROL_SCENE_MODE_FIREWORKS = 12,
  ANDROID_CONTROL_SCENE_MODE_SPORTS = 13,
  ANDROID_CONTROL_SCENE_MODE_PARTY = 14,
  ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT = 15,
  ANDROID_CONTROL_SCENE_MODE_BARCODE = 16,
  ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO = 17,
  ANDROID_CONTROL_SCENE_MODE_HDR = 18,
  ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT = 19,
  ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START = 100,
  ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END = 127,
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
[[nodiscard]] static inline std::string toString(ControlSceneMode val) {
  switch(val) {
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DISABLED:
    return "ANDROID_CONTROL_SCENE_MODE_DISABLED";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY:
    return "ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_ACTION:
    return "ANDROID_CONTROL_SCENE_MODE_ACTION";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PORTRAIT:
    return "ANDROID_CONTROL_SCENE_MODE_PORTRAIT";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_LANDSCAPE:
    return "ANDROID_CONTROL_SCENE_MODE_LANDSCAPE";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT:
    return "ANDROID_CONTROL_SCENE_MODE_NIGHT";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT:
    return "ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_THEATRE:
    return "ANDROID_CONTROL_SCENE_MODE_THEATRE";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BEACH:
    return "ANDROID_CONTROL_SCENE_MODE_BEACH";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SNOW:
    return "ANDROID_CONTROL_SCENE_MODE_SNOW";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SUNSET:
    return "ANDROID_CONTROL_SCENE_MODE_SUNSET";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO:
    return "ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FIREWORKS:
    return "ANDROID_CONTROL_SCENE_MODE_FIREWORKS";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SPORTS:
    return "ANDROID_CONTROL_SCENE_MODE_SPORTS";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PARTY:
    return "ANDROID_CONTROL_SCENE_MODE_PARTY";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT:
    return "ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BARCODE:
    return "ANDROID_CONTROL_SCENE_MODE_BARCODE";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO:
    return "ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HDR:
    return "ANDROID_CONTROL_SCENE_MODE_HDR";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT:
    return "ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START:
    return "ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START";
  case ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END:
    return "ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlSceneMode, 22> enum_values<aidl::android::hardware::camera::metadata::ControlSceneMode> = {
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DISABLED,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_ACTION,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PORTRAIT,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_LANDSCAPE,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_THEATRE,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BEACH,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SNOW,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SUNSET,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FIREWORKS,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SPORTS,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PARTY,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BARCODE,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HDR,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START,
  aidl::android::hardware::camera::metadata::ControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
