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
enum class ControlCaptureIntent : int32_t {
  ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM = 0,
  ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW = 1,
  ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE = 2,
  ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD = 3,
  ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT = 4,
  ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG = 5,
  ANDROID_CONTROL_CAPTURE_INTENT_MANUAL = 6,
  ANDROID_CONTROL_CAPTURE_INTENT_MOTION_TRACKING = 7,
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
[[nodiscard]] static inline std::string toString(ControlCaptureIntent val) {
  switch(val) {
  case ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM:
    return "ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM";
  case ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW:
    return "ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW";
  case ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE:
    return "ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE";
  case ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD:
    return "ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD";
  case ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT:
    return "ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT";
  case ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG:
    return "ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG";
  case ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_MANUAL:
    return "ANDROID_CONTROL_CAPTURE_INTENT_MANUAL";
  case ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_MOTION_TRACKING:
    return "ANDROID_CONTROL_CAPTURE_INTENT_MOTION_TRACKING";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlCaptureIntent, 8> enum_values<aidl::android::hardware::camera::metadata::ControlCaptureIntent> = {
  aidl::android::hardware::camera::metadata::ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM,
  aidl::android::hardware::camera::metadata::ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW,
  aidl::android::hardware::camera::metadata::ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE,
  aidl::android::hardware::camera::metadata::ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD,
  aidl::android::hardware::camera::metadata::ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT,
  aidl::android::hardware::camera::metadata::ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG,
  aidl::android::hardware::camera::metadata::ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_MANUAL,
  aidl::android::hardware::camera::metadata::ControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_MOTION_TRACKING,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
