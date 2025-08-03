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
enum class ControlAwbMode : int32_t {
  ANDROID_CONTROL_AWB_MODE_OFF = 0,
  ANDROID_CONTROL_AWB_MODE_AUTO = 1,
  ANDROID_CONTROL_AWB_MODE_INCANDESCENT = 2,
  ANDROID_CONTROL_AWB_MODE_FLUORESCENT = 3,
  ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT = 4,
  ANDROID_CONTROL_AWB_MODE_DAYLIGHT = 5,
  ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT = 6,
  ANDROID_CONTROL_AWB_MODE_TWILIGHT = 7,
  ANDROID_CONTROL_AWB_MODE_SHADE = 8,
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
[[nodiscard]] static inline std::string toString(ControlAwbMode val) {
  switch(val) {
  case ControlAwbMode::ANDROID_CONTROL_AWB_MODE_OFF:
    return "ANDROID_CONTROL_AWB_MODE_OFF";
  case ControlAwbMode::ANDROID_CONTROL_AWB_MODE_AUTO:
    return "ANDROID_CONTROL_AWB_MODE_AUTO";
  case ControlAwbMode::ANDROID_CONTROL_AWB_MODE_INCANDESCENT:
    return "ANDROID_CONTROL_AWB_MODE_INCANDESCENT";
  case ControlAwbMode::ANDROID_CONTROL_AWB_MODE_FLUORESCENT:
    return "ANDROID_CONTROL_AWB_MODE_FLUORESCENT";
  case ControlAwbMode::ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT:
    return "ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT";
  case ControlAwbMode::ANDROID_CONTROL_AWB_MODE_DAYLIGHT:
    return "ANDROID_CONTROL_AWB_MODE_DAYLIGHT";
  case ControlAwbMode::ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT:
    return "ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT";
  case ControlAwbMode::ANDROID_CONTROL_AWB_MODE_TWILIGHT:
    return "ANDROID_CONTROL_AWB_MODE_TWILIGHT";
  case ControlAwbMode::ANDROID_CONTROL_AWB_MODE_SHADE:
    return "ANDROID_CONTROL_AWB_MODE_SHADE";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlAwbMode, 9> enum_values<aidl::android::hardware::camera::metadata::ControlAwbMode> = {
  aidl::android::hardware::camera::metadata::ControlAwbMode::ANDROID_CONTROL_AWB_MODE_OFF,
  aidl::android::hardware::camera::metadata::ControlAwbMode::ANDROID_CONTROL_AWB_MODE_AUTO,
  aidl::android::hardware::camera::metadata::ControlAwbMode::ANDROID_CONTROL_AWB_MODE_INCANDESCENT,
  aidl::android::hardware::camera::metadata::ControlAwbMode::ANDROID_CONTROL_AWB_MODE_FLUORESCENT,
  aidl::android::hardware::camera::metadata::ControlAwbMode::ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT,
  aidl::android::hardware::camera::metadata::ControlAwbMode::ANDROID_CONTROL_AWB_MODE_DAYLIGHT,
  aidl::android::hardware::camera::metadata::ControlAwbMode::ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
  aidl::android::hardware::camera::metadata::ControlAwbMode::ANDROID_CONTROL_AWB_MODE_TWILIGHT,
  aidl::android::hardware::camera::metadata::ControlAwbMode::ANDROID_CONTROL_AWB_MODE_SHADE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
