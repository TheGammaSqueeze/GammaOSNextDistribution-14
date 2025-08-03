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
enum class TonemapMode : int32_t {
  ANDROID_TONEMAP_MODE_CONTRAST_CURVE = 0,
  ANDROID_TONEMAP_MODE_FAST = 1,
  ANDROID_TONEMAP_MODE_HIGH_QUALITY = 2,
  ANDROID_TONEMAP_MODE_GAMMA_VALUE = 3,
  ANDROID_TONEMAP_MODE_PRESET_CURVE = 4,
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
[[nodiscard]] static inline std::string toString(TonemapMode val) {
  switch(val) {
  case TonemapMode::ANDROID_TONEMAP_MODE_CONTRAST_CURVE:
    return "ANDROID_TONEMAP_MODE_CONTRAST_CURVE";
  case TonemapMode::ANDROID_TONEMAP_MODE_FAST:
    return "ANDROID_TONEMAP_MODE_FAST";
  case TonemapMode::ANDROID_TONEMAP_MODE_HIGH_QUALITY:
    return "ANDROID_TONEMAP_MODE_HIGH_QUALITY";
  case TonemapMode::ANDROID_TONEMAP_MODE_GAMMA_VALUE:
    return "ANDROID_TONEMAP_MODE_GAMMA_VALUE";
  case TonemapMode::ANDROID_TONEMAP_MODE_PRESET_CURVE:
    return "ANDROID_TONEMAP_MODE_PRESET_CURVE";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::TonemapMode, 5> enum_values<aidl::android::hardware::camera::metadata::TonemapMode> = {
  aidl::android::hardware::camera::metadata::TonemapMode::ANDROID_TONEMAP_MODE_CONTRAST_CURVE,
  aidl::android::hardware::camera::metadata::TonemapMode::ANDROID_TONEMAP_MODE_FAST,
  aidl::android::hardware::camera::metadata::TonemapMode::ANDROID_TONEMAP_MODE_HIGH_QUALITY,
  aidl::android::hardware::camera::metadata::TonemapMode::ANDROID_TONEMAP_MODE_GAMMA_VALUE,
  aidl::android::hardware::camera::metadata::TonemapMode::ANDROID_TONEMAP_MODE_PRESET_CURVE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
