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
enum class NoiseReductionMode : int32_t {
  ANDROID_NOISE_REDUCTION_MODE_OFF = 0,
  ANDROID_NOISE_REDUCTION_MODE_FAST = 1,
  ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY = 2,
  ANDROID_NOISE_REDUCTION_MODE_MINIMAL = 3,
  ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG = 4,
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
[[nodiscard]] static inline std::string toString(NoiseReductionMode val) {
  switch(val) {
  case NoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_OFF:
    return "ANDROID_NOISE_REDUCTION_MODE_OFF";
  case NoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_FAST:
    return "ANDROID_NOISE_REDUCTION_MODE_FAST";
  case NoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY:
    return "ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY";
  case NoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_MINIMAL:
    return "ANDROID_NOISE_REDUCTION_MODE_MINIMAL";
  case NoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG:
    return "ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::NoiseReductionMode, 5> enum_values<aidl::android::hardware::camera::metadata::NoiseReductionMode> = {
  aidl::android::hardware::camera::metadata::NoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_OFF,
  aidl::android::hardware::camera::metadata::NoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_FAST,
  aidl::android::hardware::camera::metadata::NoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY,
  aidl::android::hardware::camera::metadata::NoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_MINIMAL,
  aidl::android::hardware::camera::metadata::NoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
