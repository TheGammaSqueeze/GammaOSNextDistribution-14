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
enum class TonemapPresetCurve : int32_t {
  ANDROID_TONEMAP_PRESET_CURVE_SRGB = 0,
  ANDROID_TONEMAP_PRESET_CURVE_REC709 = 1,
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
[[nodiscard]] static inline std::string toString(TonemapPresetCurve val) {
  switch(val) {
  case TonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_SRGB:
    return "ANDROID_TONEMAP_PRESET_CURVE_SRGB";
  case TonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_REC709:
    return "ANDROID_TONEMAP_PRESET_CURVE_REC709";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::TonemapPresetCurve, 2> enum_values<aidl::android::hardware::camera::metadata::TonemapPresetCurve> = {
  aidl::android::hardware::camera::metadata::TonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_SRGB,
  aidl::android::hardware::camera::metadata::TonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_REC709,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
