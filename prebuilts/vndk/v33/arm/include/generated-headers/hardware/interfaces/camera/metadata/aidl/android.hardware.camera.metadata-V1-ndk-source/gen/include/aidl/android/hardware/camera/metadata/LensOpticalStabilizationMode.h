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
enum class LensOpticalStabilizationMode : int32_t {
  ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF = 0,
  ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON = 1,
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
[[nodiscard]] static inline std::string toString(LensOpticalStabilizationMode val) {
  switch(val) {
  case LensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF:
    return "ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF";
  case LensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON:
    return "ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::LensOpticalStabilizationMode, 2> enum_values<aidl::android::hardware::camera::metadata::LensOpticalStabilizationMode> = {
  aidl::android::hardware::camera::metadata::LensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF,
  aidl::android::hardware::camera::metadata::LensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
