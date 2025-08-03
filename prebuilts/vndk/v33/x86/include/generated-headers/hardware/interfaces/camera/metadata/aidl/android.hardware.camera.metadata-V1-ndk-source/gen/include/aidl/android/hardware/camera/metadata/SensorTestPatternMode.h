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
enum class SensorTestPatternMode : int32_t {
  ANDROID_SENSOR_TEST_PATTERN_MODE_OFF = 0,
  ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR = 1,
  ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS = 2,
  ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY = 3,
  ANDROID_SENSOR_TEST_PATTERN_MODE_PN9 = 4,
  ANDROID_SENSOR_TEST_PATTERN_MODE_BLACK = 5,
  ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1 = 256,
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
[[nodiscard]] static inline std::string toString(SensorTestPatternMode val) {
  switch(val) {
  case SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_OFF:
    return "ANDROID_SENSOR_TEST_PATTERN_MODE_OFF";
  case SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR:
    return "ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR";
  case SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS:
    return "ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS";
  case SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY:
    return "ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY";
  case SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_PN9:
    return "ANDROID_SENSOR_TEST_PATTERN_MODE_PN9";
  case SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_BLACK:
    return "ANDROID_SENSOR_TEST_PATTERN_MODE_BLACK";
  case SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1:
    return "ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::SensorTestPatternMode, 7> enum_values<aidl::android::hardware::camera::metadata::SensorTestPatternMode> = {
  aidl::android::hardware::camera::metadata::SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_OFF,
  aidl::android::hardware::camera::metadata::SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR,
  aidl::android::hardware::camera::metadata::SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS,
  aidl::android::hardware::camera::metadata::SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY,
  aidl::android::hardware::camera::metadata::SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_PN9,
  aidl::android::hardware::camera::metadata::SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_BLACK,
  aidl::android::hardware::camera::metadata::SensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
