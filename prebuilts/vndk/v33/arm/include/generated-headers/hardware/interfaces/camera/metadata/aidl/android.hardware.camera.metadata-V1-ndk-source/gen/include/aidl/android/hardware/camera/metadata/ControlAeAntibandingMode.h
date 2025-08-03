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
enum class ControlAeAntibandingMode : int32_t {
  ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF = 0,
  ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ = 1,
  ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ = 2,
  ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO = 3,
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
[[nodiscard]] static inline std::string toString(ControlAeAntibandingMode val) {
  switch(val) {
  case ControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF:
    return "ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF";
  case ControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ:
    return "ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ";
  case ControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ:
    return "ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ";
  case ControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO:
    return "ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ControlAeAntibandingMode, 4> enum_values<aidl::android::hardware::camera::metadata::ControlAeAntibandingMode> = {
  aidl::android::hardware::camera::metadata::ControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF,
  aidl::android::hardware::camera::metadata::ControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ,
  aidl::android::hardware::camera::metadata::ControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ,
  aidl::android::hardware::camera::metadata::ControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
