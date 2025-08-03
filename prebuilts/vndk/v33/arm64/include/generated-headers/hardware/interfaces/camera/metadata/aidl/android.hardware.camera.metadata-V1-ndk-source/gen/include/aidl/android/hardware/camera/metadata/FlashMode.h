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
enum class FlashMode : int32_t {
  ANDROID_FLASH_MODE_OFF = 0,
  ANDROID_FLASH_MODE_SINGLE = 1,
  ANDROID_FLASH_MODE_TORCH = 2,
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
[[nodiscard]] static inline std::string toString(FlashMode val) {
  switch(val) {
  case FlashMode::ANDROID_FLASH_MODE_OFF:
    return "ANDROID_FLASH_MODE_OFF";
  case FlashMode::ANDROID_FLASH_MODE_SINGLE:
    return "ANDROID_FLASH_MODE_SINGLE";
  case FlashMode::ANDROID_FLASH_MODE_TORCH:
    return "ANDROID_FLASH_MODE_TORCH";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::FlashMode, 3> enum_values<aidl::android::hardware::camera::metadata::FlashMode> = {
  aidl::android::hardware::camera::metadata::FlashMode::ANDROID_FLASH_MODE_OFF,
  aidl::android::hardware::camera::metadata::FlashMode::ANDROID_FLASH_MODE_SINGLE,
  aidl::android::hardware::camera::metadata::FlashMode::ANDROID_FLASH_MODE_TORCH,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
