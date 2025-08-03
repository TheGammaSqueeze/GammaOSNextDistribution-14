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
enum class ControlVideoStabilizationMode : int32_t {
  ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF = 0,
  ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON = 1,
  ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION = 2,
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
[[nodiscard]] static inline std::string toString(ControlVideoStabilizationMode val) {
  switch(val) {
  case ControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF:
    return "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF";
  case ControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON:
    return "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON";
  case ControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION:
    return "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlVideoStabilizationMode, 3> enum_values<aidl::android::hardware::camera::metadata::ControlVideoStabilizationMode> = {
  aidl::android::hardware::camera::metadata::ControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF,
  aidl::android::hardware::camera::metadata::ControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON,
  aidl::android::hardware::camera::metadata::ControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
