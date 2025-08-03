#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <array>
#include <android/binder_enums.h>
namespace aidl {
namespace android {
namespace hardware {
namespace power {
enum class Mode : int32_t {
  DOUBLE_TAP_TO_WAKE = 0,
  LOW_POWER = 1,
  SUSTAINED_PERFORMANCE = 2,
  FIXED_PERFORMANCE = 3,
  VR = 4,
  LAUNCH = 5,
  EXPENSIVE_RENDERING = 6,
  INTERACTIVE = 7,
  DEVICE_IDLE = 8,
  DISPLAY_INACTIVE = 9,
  AUDIO_STREAMING_LOW_LATENCY = 10,
  CAMERA_STREAMING_SECURE = 11,
  CAMERA_STREAMING_LOW = 12,
  CAMERA_STREAMING_MID = 13,
  CAMERA_STREAMING_HIGH = 14,
};

static inline std::string toString(Mode val) {
  switch(val) {
  case Mode::DOUBLE_TAP_TO_WAKE:
    return "DOUBLE_TAP_TO_WAKE";
  case Mode::LOW_POWER:
    return "LOW_POWER";
  case Mode::SUSTAINED_PERFORMANCE:
    return "SUSTAINED_PERFORMANCE";
  case Mode::FIXED_PERFORMANCE:
    return "FIXED_PERFORMANCE";
  case Mode::VR:
    return "VR";
  case Mode::LAUNCH:
    return "LAUNCH";
  case Mode::EXPENSIVE_RENDERING:
    return "EXPENSIVE_RENDERING";
  case Mode::INTERACTIVE:
    return "INTERACTIVE";
  case Mode::DEVICE_IDLE:
    return "DEVICE_IDLE";
  case Mode::DISPLAY_INACTIVE:
    return "DISPLAY_INACTIVE";
  case Mode::AUDIO_STREAMING_LOW_LATENCY:
    return "AUDIO_STREAMING_LOW_LATENCY";
  case Mode::CAMERA_STREAMING_SECURE:
    return "CAMERA_STREAMING_SECURE";
  case Mode::CAMERA_STREAMING_LOW:
    return "CAMERA_STREAMING_LOW";
  case Mode::CAMERA_STREAMING_MID:
    return "CAMERA_STREAMING_MID";
  case Mode::CAMERA_STREAMING_HIGH:
    return "CAMERA_STREAMING_HIGH";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::power::Mode, 15> enum_values<aidl::android::hardware::power::Mode> = {
  aidl::android::hardware::power::Mode::DOUBLE_TAP_TO_WAKE,
  aidl::android::hardware::power::Mode::LOW_POWER,
  aidl::android::hardware::power::Mode::SUSTAINED_PERFORMANCE,
  aidl::android::hardware::power::Mode::FIXED_PERFORMANCE,
  aidl::android::hardware::power::Mode::VR,
  aidl::android::hardware::power::Mode::LAUNCH,
  aidl::android::hardware::power::Mode::EXPENSIVE_RENDERING,
  aidl::android::hardware::power::Mode::INTERACTIVE,
  aidl::android::hardware::power::Mode::DEVICE_IDLE,
  aidl::android::hardware::power::Mode::DISPLAY_INACTIVE,
  aidl::android::hardware::power::Mode::AUDIO_STREAMING_LOW_LATENCY,
  aidl::android::hardware::power::Mode::CAMERA_STREAMING_SECURE,
  aidl::android::hardware::power::Mode::CAMERA_STREAMING_LOW,
  aidl::android::hardware::power::Mode::CAMERA_STREAMING_MID,
  aidl::android::hardware::power::Mode::CAMERA_STREAMING_HIGH,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
