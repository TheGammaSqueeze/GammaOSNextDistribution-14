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
enum class ControlAeMode : int32_t {
  ANDROID_CONTROL_AE_MODE_OFF = 0,
  ANDROID_CONTROL_AE_MODE_ON = 1,
  ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH = 2,
  ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH = 3,
  ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE = 4,
  ANDROID_CONTROL_AE_MODE_ON_EXTERNAL_FLASH = 5,
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
[[nodiscard]] static inline std::string toString(ControlAeMode val) {
  switch(val) {
  case ControlAeMode::ANDROID_CONTROL_AE_MODE_OFF:
    return "ANDROID_CONTROL_AE_MODE_OFF";
  case ControlAeMode::ANDROID_CONTROL_AE_MODE_ON:
    return "ANDROID_CONTROL_AE_MODE_ON";
  case ControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH:
    return "ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH";
  case ControlAeMode::ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH:
    return "ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH";
  case ControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE:
    return "ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE";
  case ControlAeMode::ANDROID_CONTROL_AE_MODE_ON_EXTERNAL_FLASH:
    return "ANDROID_CONTROL_AE_MODE_ON_EXTERNAL_FLASH";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlAeMode, 6> enum_values<aidl::android::hardware::camera::metadata::ControlAeMode> = {
  aidl::android::hardware::camera::metadata::ControlAeMode::ANDROID_CONTROL_AE_MODE_OFF,
  aidl::android::hardware::camera::metadata::ControlAeMode::ANDROID_CONTROL_AE_MODE_ON,
  aidl::android::hardware::camera::metadata::ControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH,
  aidl::android::hardware::camera::metadata::ControlAeMode::ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH,
  aidl::android::hardware::camera::metadata::ControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE,
  aidl::android::hardware::camera::metadata::ControlAeMode::ANDROID_CONTROL_AE_MODE_ON_EXTERNAL_FLASH,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
