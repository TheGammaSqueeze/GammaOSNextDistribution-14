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
enum class SensorInfoColorFilterArrangement : int32_t {
  ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB = 0,
  ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG = 1,
  ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG = 2,
  ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR = 3,
  ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB = 4,
  ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO = 5,
  ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR = 6,
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
[[nodiscard]] static inline std::string toString(SensorInfoColorFilterArrangement val) {
  switch(val) {
  case SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB:
    return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB";
  case SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG:
    return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG";
  case SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG:
    return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG";
  case SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR:
    return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR";
  case SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB:
    return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB";
  case SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO:
    return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO";
  case SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR:
    return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::SensorInfoColorFilterArrangement, 7> enum_values<aidl::android::hardware::camera::metadata::SensorInfoColorFilterArrangement> = {
  aidl::android::hardware::camera::metadata::SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB,
  aidl::android::hardware::camera::metadata::SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG,
  aidl::android::hardware::camera::metadata::SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG,
  aidl::android::hardware::camera::metadata::SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR,
  aidl::android::hardware::camera::metadata::SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB,
  aidl::android::hardware::camera::metadata::SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO,
  aidl::android::hardware::camera::metadata::SensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
