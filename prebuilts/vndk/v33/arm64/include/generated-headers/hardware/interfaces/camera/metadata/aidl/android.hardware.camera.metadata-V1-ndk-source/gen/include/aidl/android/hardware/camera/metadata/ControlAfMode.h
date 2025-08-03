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
enum class ControlAfMode : int32_t {
  ANDROID_CONTROL_AF_MODE_OFF = 0,
  ANDROID_CONTROL_AF_MODE_AUTO = 1,
  ANDROID_CONTROL_AF_MODE_MACRO = 2,
  ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO = 3,
  ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE = 4,
  ANDROID_CONTROL_AF_MODE_EDOF = 5,
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
[[nodiscard]] static inline std::string toString(ControlAfMode val) {
  switch(val) {
  case ControlAfMode::ANDROID_CONTROL_AF_MODE_OFF:
    return "ANDROID_CONTROL_AF_MODE_OFF";
  case ControlAfMode::ANDROID_CONTROL_AF_MODE_AUTO:
    return "ANDROID_CONTROL_AF_MODE_AUTO";
  case ControlAfMode::ANDROID_CONTROL_AF_MODE_MACRO:
    return "ANDROID_CONTROL_AF_MODE_MACRO";
  case ControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO:
    return "ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO";
  case ControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE:
    return "ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE";
  case ControlAfMode::ANDROID_CONTROL_AF_MODE_EDOF:
    return "ANDROID_CONTROL_AF_MODE_EDOF";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlAfMode, 6> enum_values<aidl::android::hardware::camera::metadata::ControlAfMode> = {
  aidl::android::hardware::camera::metadata::ControlAfMode::ANDROID_CONTROL_AF_MODE_OFF,
  aidl::android::hardware::camera::metadata::ControlAfMode::ANDROID_CONTROL_AF_MODE_AUTO,
  aidl::android::hardware::camera::metadata::ControlAfMode::ANDROID_CONTROL_AF_MODE_MACRO,
  aidl::android::hardware::camera::metadata::ControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO,
  aidl::android::hardware::camera::metadata::ControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE,
  aidl::android::hardware::camera::metadata::ControlAfMode::ANDROID_CONTROL_AF_MODE_EDOF,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
