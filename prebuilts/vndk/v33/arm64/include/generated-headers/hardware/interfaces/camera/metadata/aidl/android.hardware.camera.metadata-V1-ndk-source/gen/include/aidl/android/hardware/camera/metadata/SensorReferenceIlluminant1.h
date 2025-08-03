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
enum class SensorReferenceIlluminant1 : int32_t {
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT = 1,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT = 2,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN = 3,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH = 4,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER = 9,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER = 10,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE = 11,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT = 12,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT = 13,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT = 14,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT = 15,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A = 17,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B = 18,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C = 19,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55 = 20,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65 = 21,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75 = 22,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50 = 23,
  ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN = 24,
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
[[nodiscard]] static inline std::string toString(SensorReferenceIlluminant1 val) {
  switch(val) {
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50";
  case SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN:
    return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1, 19> enum_values<aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1> = {
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50,
  aidl::android::hardware::camera::metadata::SensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
