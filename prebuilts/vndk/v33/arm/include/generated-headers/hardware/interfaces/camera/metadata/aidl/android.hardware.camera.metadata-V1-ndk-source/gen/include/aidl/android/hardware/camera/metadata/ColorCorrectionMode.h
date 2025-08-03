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
enum class ColorCorrectionMode : int32_t {
  ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX = 0,
  ANDROID_COLOR_CORRECTION_MODE_FAST = 1,
  ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY = 2,
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
[[nodiscard]] static inline std::string toString(ColorCorrectionMode val) {
  switch(val) {
  case ColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX:
    return "ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX";
  case ColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_FAST:
    return "ANDROID_COLOR_CORRECTION_MODE_FAST";
  case ColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY:
    return "ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ColorCorrectionMode, 3> enum_values<aidl::android::hardware::camera::metadata::ColorCorrectionMode> = {
  aidl::android::hardware::camera::metadata::ColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX,
  aidl::android::hardware::camera::metadata::ColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_FAST,
  aidl::android::hardware::camera::metadata::ColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
