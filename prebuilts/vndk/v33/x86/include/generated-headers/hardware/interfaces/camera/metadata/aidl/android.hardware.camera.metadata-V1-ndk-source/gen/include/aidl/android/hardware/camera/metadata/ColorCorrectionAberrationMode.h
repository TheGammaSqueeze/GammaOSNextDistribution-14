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
enum class ColorCorrectionAberrationMode : int32_t {
  ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF = 0,
  ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST = 1,
  ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY = 2,
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
[[nodiscard]] static inline std::string toString(ColorCorrectionAberrationMode val) {
  switch(val) {
  case ColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF:
    return "ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF";
  case ColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST:
    return "ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST";
  case ColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY:
    return "ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ColorCorrectionAberrationMode, 3> enum_values<aidl::android::hardware::camera::metadata::ColorCorrectionAberrationMode> = {
  aidl::android::hardware::camera::metadata::ColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF,
  aidl::android::hardware::camera::metadata::ColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST,
  aidl::android::hardware::camera::metadata::ColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
