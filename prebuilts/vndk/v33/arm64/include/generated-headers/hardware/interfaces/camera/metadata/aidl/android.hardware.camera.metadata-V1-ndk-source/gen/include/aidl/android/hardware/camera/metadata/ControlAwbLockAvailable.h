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
enum class ControlAwbLockAvailable : int32_t {
  ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE = 0,
  ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE = 1,
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
[[nodiscard]] static inline std::string toString(ControlAwbLockAvailable val) {
  switch(val) {
  case ControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE:
    return "ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE";
  case ControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE:
    return "ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlAwbLockAvailable, 2> enum_values<aidl::android::hardware::camera::metadata::ControlAwbLockAvailable> = {
  aidl::android::hardware::camera::metadata::ControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE,
  aidl::android::hardware::camera::metadata::ControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
