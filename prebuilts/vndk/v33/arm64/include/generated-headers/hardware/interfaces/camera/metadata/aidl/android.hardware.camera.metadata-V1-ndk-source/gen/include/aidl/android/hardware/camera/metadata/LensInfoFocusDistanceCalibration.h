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
enum class LensInfoFocusDistanceCalibration : int32_t {
  ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED = 0,
  ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE = 1,
  ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED = 2,
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
[[nodiscard]] static inline std::string toString(LensInfoFocusDistanceCalibration val) {
  switch(val) {
  case LensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED:
    return "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED";
  case LensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE:
    return "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE";
  case LensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED:
    return "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::LensInfoFocusDistanceCalibration, 3> enum_values<aidl::android::hardware::camera::metadata::LensInfoFocusDistanceCalibration> = {
  aidl::android::hardware::camera::metadata::LensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED,
  aidl::android::hardware::camera::metadata::LensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE,
  aidl::android::hardware::camera::metadata::LensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
