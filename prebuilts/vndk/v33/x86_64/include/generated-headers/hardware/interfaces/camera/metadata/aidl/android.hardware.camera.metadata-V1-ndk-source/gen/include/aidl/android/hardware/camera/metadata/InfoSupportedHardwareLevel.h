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
enum class InfoSupportedHardwareLevel : int32_t {
  ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED = 0,
  ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL = 1,
  ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY = 2,
  ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3 = 3,
  ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL = 4,
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
[[nodiscard]] static inline std::string toString(InfoSupportedHardwareLevel val) {
  switch(val) {
  case InfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
    return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED";
  case InfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
    return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL";
  case InfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
    return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY";
  case InfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3:
    return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3";
  case InfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL:
    return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::InfoSupportedHardwareLevel, 5> enum_values<aidl::android::hardware::camera::metadata::InfoSupportedHardwareLevel> = {
  aidl::android::hardware::camera::metadata::InfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
  aidl::android::hardware::camera::metadata::InfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
  aidl::android::hardware::camera::metadata::InfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
  aidl::android::hardware::camera::metadata::InfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3,
  aidl::android::hardware::camera::metadata::InfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
