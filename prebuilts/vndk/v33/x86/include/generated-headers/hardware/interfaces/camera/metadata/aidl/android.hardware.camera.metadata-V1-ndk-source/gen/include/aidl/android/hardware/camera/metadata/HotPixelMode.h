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
enum class HotPixelMode : int32_t {
  ANDROID_HOT_PIXEL_MODE_OFF = 0,
  ANDROID_HOT_PIXEL_MODE_FAST = 1,
  ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY = 2,
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
[[nodiscard]] static inline std::string toString(HotPixelMode val) {
  switch(val) {
  case HotPixelMode::ANDROID_HOT_PIXEL_MODE_OFF:
    return "ANDROID_HOT_PIXEL_MODE_OFF";
  case HotPixelMode::ANDROID_HOT_PIXEL_MODE_FAST:
    return "ANDROID_HOT_PIXEL_MODE_FAST";
  case HotPixelMode::ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY:
    return "ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::HotPixelMode, 3> enum_values<aidl::android::hardware::camera::metadata::HotPixelMode> = {
  aidl::android::hardware::camera::metadata::HotPixelMode::ANDROID_HOT_PIXEL_MODE_OFF,
  aidl::android::hardware::camera::metadata::HotPixelMode::ANDROID_HOT_PIXEL_MODE_FAST,
  aidl::android::hardware::camera::metadata::HotPixelMode::ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
