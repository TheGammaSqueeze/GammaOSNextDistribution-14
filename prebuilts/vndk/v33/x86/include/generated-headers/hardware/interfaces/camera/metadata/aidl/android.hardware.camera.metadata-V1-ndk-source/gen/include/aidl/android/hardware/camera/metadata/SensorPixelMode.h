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
enum class SensorPixelMode : int32_t {
  ANDROID_SENSOR_PIXEL_MODE_DEFAULT = 0,
  ANDROID_SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION = 1,
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
[[nodiscard]] static inline std::string toString(SensorPixelMode val) {
  switch(val) {
  case SensorPixelMode::ANDROID_SENSOR_PIXEL_MODE_DEFAULT:
    return "ANDROID_SENSOR_PIXEL_MODE_DEFAULT";
  case SensorPixelMode::ANDROID_SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION:
    return "ANDROID_SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::SensorPixelMode, 2> enum_values<aidl::android::hardware::camera::metadata::SensorPixelMode> = {
  aidl::android::hardware::camera::metadata::SensorPixelMode::ANDROID_SENSOR_PIXEL_MODE_DEFAULT,
  aidl::android::hardware::camera::metadata::SensorPixelMode::ANDROID_SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
