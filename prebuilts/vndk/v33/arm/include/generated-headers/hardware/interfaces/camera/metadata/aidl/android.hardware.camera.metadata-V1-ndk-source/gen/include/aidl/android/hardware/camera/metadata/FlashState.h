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
enum class FlashState : int32_t {
  ANDROID_FLASH_STATE_UNAVAILABLE = 0,
  ANDROID_FLASH_STATE_CHARGING = 1,
  ANDROID_FLASH_STATE_READY = 2,
  ANDROID_FLASH_STATE_FIRED = 3,
  ANDROID_FLASH_STATE_PARTIAL = 4,
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
[[nodiscard]] static inline std::string toString(FlashState val) {
  switch(val) {
  case FlashState::ANDROID_FLASH_STATE_UNAVAILABLE:
    return "ANDROID_FLASH_STATE_UNAVAILABLE";
  case FlashState::ANDROID_FLASH_STATE_CHARGING:
    return "ANDROID_FLASH_STATE_CHARGING";
  case FlashState::ANDROID_FLASH_STATE_READY:
    return "ANDROID_FLASH_STATE_READY";
  case FlashState::ANDROID_FLASH_STATE_FIRED:
    return "ANDROID_FLASH_STATE_FIRED";
  case FlashState::ANDROID_FLASH_STATE_PARTIAL:
    return "ANDROID_FLASH_STATE_PARTIAL";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::FlashState, 5> enum_values<aidl::android::hardware::camera::metadata::FlashState> = {
  aidl::android::hardware::camera::metadata::FlashState::ANDROID_FLASH_STATE_UNAVAILABLE,
  aidl::android::hardware::camera::metadata::FlashState::ANDROID_FLASH_STATE_CHARGING,
  aidl::android::hardware::camera::metadata::FlashState::ANDROID_FLASH_STATE_READY,
  aidl::android::hardware::camera::metadata::FlashState::ANDROID_FLASH_STATE_FIRED,
  aidl::android::hardware::camera::metadata::FlashState::ANDROID_FLASH_STATE_PARTIAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
