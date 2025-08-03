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
enum class ControlAfState : int32_t {
  ANDROID_CONTROL_AF_STATE_INACTIVE = 0,
  ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN = 1,
  ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED = 2,
  ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN = 3,
  ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED = 4,
  ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED = 5,
  ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED = 6,
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
[[nodiscard]] static inline std::string toString(ControlAfState val) {
  switch(val) {
  case ControlAfState::ANDROID_CONTROL_AF_STATE_INACTIVE:
    return "ANDROID_CONTROL_AF_STATE_INACTIVE";
  case ControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN:
    return "ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN";
  case ControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED:
    return "ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED";
  case ControlAfState::ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN:
    return "ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN";
  case ControlAfState::ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED:
    return "ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED";
  case ControlAfState::ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
    return "ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED";
  case ControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
    return "ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlAfState, 7> enum_values<aidl::android::hardware::camera::metadata::ControlAfState> = {
  aidl::android::hardware::camera::metadata::ControlAfState::ANDROID_CONTROL_AF_STATE_INACTIVE,
  aidl::android::hardware::camera::metadata::ControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN,
  aidl::android::hardware::camera::metadata::ControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED,
  aidl::android::hardware::camera::metadata::ControlAfState::ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN,
  aidl::android::hardware::camera::metadata::ControlAfState::ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED,
  aidl::android::hardware::camera::metadata::ControlAfState::ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED,
  aidl::android::hardware::camera::metadata::ControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
