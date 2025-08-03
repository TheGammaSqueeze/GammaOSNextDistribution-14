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
enum class ControlAwbState : int32_t {
  ANDROID_CONTROL_AWB_STATE_INACTIVE = 0,
  ANDROID_CONTROL_AWB_STATE_SEARCHING = 1,
  ANDROID_CONTROL_AWB_STATE_CONVERGED = 2,
  ANDROID_CONTROL_AWB_STATE_LOCKED = 3,
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
[[nodiscard]] static inline std::string toString(ControlAwbState val) {
  switch(val) {
  case ControlAwbState::ANDROID_CONTROL_AWB_STATE_INACTIVE:
    return "ANDROID_CONTROL_AWB_STATE_INACTIVE";
  case ControlAwbState::ANDROID_CONTROL_AWB_STATE_SEARCHING:
    return "ANDROID_CONTROL_AWB_STATE_SEARCHING";
  case ControlAwbState::ANDROID_CONTROL_AWB_STATE_CONVERGED:
    return "ANDROID_CONTROL_AWB_STATE_CONVERGED";
  case ControlAwbState::ANDROID_CONTROL_AWB_STATE_LOCKED:
    return "ANDROID_CONTROL_AWB_STATE_LOCKED";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlAwbState, 4> enum_values<aidl::android::hardware::camera::metadata::ControlAwbState> = {
  aidl::android::hardware::camera::metadata::ControlAwbState::ANDROID_CONTROL_AWB_STATE_INACTIVE,
  aidl::android::hardware::camera::metadata::ControlAwbState::ANDROID_CONTROL_AWB_STATE_SEARCHING,
  aidl::android::hardware::camera::metadata::ControlAwbState::ANDROID_CONTROL_AWB_STATE_CONVERGED,
  aidl::android::hardware::camera::metadata::ControlAwbState::ANDROID_CONTROL_AWB_STATE_LOCKED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
