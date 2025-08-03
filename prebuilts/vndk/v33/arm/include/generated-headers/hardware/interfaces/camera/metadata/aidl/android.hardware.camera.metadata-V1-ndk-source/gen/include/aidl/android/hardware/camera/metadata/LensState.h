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
enum class LensState : int32_t {
  ANDROID_LENS_STATE_STATIONARY = 0,
  ANDROID_LENS_STATE_MOVING = 1,
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
[[nodiscard]] static inline std::string toString(LensState val) {
  switch(val) {
  case LensState::ANDROID_LENS_STATE_STATIONARY:
    return "ANDROID_LENS_STATE_STATIONARY";
  case LensState::ANDROID_LENS_STATE_MOVING:
    return "ANDROID_LENS_STATE_MOVING";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::LensState, 2> enum_values<aidl::android::hardware::camera::metadata::LensState> = {
  aidl::android::hardware::camera::metadata::LensState::ANDROID_LENS_STATE_STATIONARY,
  aidl::android::hardware::camera::metadata::LensState::ANDROID_LENS_STATE_MOVING,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
