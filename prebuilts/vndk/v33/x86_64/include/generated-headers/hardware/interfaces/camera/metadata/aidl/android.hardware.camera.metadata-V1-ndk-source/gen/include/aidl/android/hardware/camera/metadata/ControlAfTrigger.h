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
enum class ControlAfTrigger : int32_t {
  ANDROID_CONTROL_AF_TRIGGER_IDLE = 0,
  ANDROID_CONTROL_AF_TRIGGER_START = 1,
  ANDROID_CONTROL_AF_TRIGGER_CANCEL = 2,
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
[[nodiscard]] static inline std::string toString(ControlAfTrigger val) {
  switch(val) {
  case ControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_IDLE:
    return "ANDROID_CONTROL_AF_TRIGGER_IDLE";
  case ControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_START:
    return "ANDROID_CONTROL_AF_TRIGGER_START";
  case ControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_CANCEL:
    return "ANDROID_CONTROL_AF_TRIGGER_CANCEL";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlAfTrigger, 3> enum_values<aidl::android::hardware::camera::metadata::ControlAfTrigger> = {
  aidl::android::hardware::camera::metadata::ControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_IDLE,
  aidl::android::hardware::camera::metadata::ControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_START,
  aidl::android::hardware::camera::metadata::ControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_CANCEL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
