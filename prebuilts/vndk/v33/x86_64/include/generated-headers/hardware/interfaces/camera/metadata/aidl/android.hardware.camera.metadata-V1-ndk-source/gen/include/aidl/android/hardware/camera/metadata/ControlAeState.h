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
enum class ControlAeState : int32_t {
  ANDROID_CONTROL_AE_STATE_INACTIVE = 0,
  ANDROID_CONTROL_AE_STATE_SEARCHING = 1,
  ANDROID_CONTROL_AE_STATE_CONVERGED = 2,
  ANDROID_CONTROL_AE_STATE_LOCKED = 3,
  ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED = 4,
  ANDROID_CONTROL_AE_STATE_PRECAPTURE = 5,
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
[[nodiscard]] static inline std::string toString(ControlAeState val) {
  switch(val) {
  case ControlAeState::ANDROID_CONTROL_AE_STATE_INACTIVE:
    return "ANDROID_CONTROL_AE_STATE_INACTIVE";
  case ControlAeState::ANDROID_CONTROL_AE_STATE_SEARCHING:
    return "ANDROID_CONTROL_AE_STATE_SEARCHING";
  case ControlAeState::ANDROID_CONTROL_AE_STATE_CONVERGED:
    return "ANDROID_CONTROL_AE_STATE_CONVERGED";
  case ControlAeState::ANDROID_CONTROL_AE_STATE_LOCKED:
    return "ANDROID_CONTROL_AE_STATE_LOCKED";
  case ControlAeState::ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED:
    return "ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED";
  case ControlAeState::ANDROID_CONTROL_AE_STATE_PRECAPTURE:
    return "ANDROID_CONTROL_AE_STATE_PRECAPTURE";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlAeState, 6> enum_values<aidl::android::hardware::camera::metadata::ControlAeState> = {
  aidl::android::hardware::camera::metadata::ControlAeState::ANDROID_CONTROL_AE_STATE_INACTIVE,
  aidl::android::hardware::camera::metadata::ControlAeState::ANDROID_CONTROL_AE_STATE_SEARCHING,
  aidl::android::hardware::camera::metadata::ControlAeState::ANDROID_CONTROL_AE_STATE_CONVERGED,
  aidl::android::hardware::camera::metadata::ControlAeState::ANDROID_CONTROL_AE_STATE_LOCKED,
  aidl::android::hardware::camera::metadata::ControlAeState::ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED,
  aidl::android::hardware::camera::metadata::ControlAeState::ANDROID_CONTROL_AE_STATE_PRECAPTURE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
