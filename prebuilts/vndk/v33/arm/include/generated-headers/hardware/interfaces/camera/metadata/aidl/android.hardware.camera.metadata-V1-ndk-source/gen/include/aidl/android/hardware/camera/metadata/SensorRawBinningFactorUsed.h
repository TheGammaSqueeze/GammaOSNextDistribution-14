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
enum class SensorRawBinningFactorUsed : int32_t {
  ANDROID_SENSOR_RAW_BINNING_FACTOR_USED_TRUE = 0,
  ANDROID_SENSOR_RAW_BINNING_FACTOR_USED_FALSE = 1,
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
[[nodiscard]] static inline std::string toString(SensorRawBinningFactorUsed val) {
  switch(val) {
  case SensorRawBinningFactorUsed::ANDROID_SENSOR_RAW_BINNING_FACTOR_USED_TRUE:
    return "ANDROID_SENSOR_RAW_BINNING_FACTOR_USED_TRUE";
  case SensorRawBinningFactorUsed::ANDROID_SENSOR_RAW_BINNING_FACTOR_USED_FALSE:
    return "ANDROID_SENSOR_RAW_BINNING_FACTOR_USED_FALSE";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::SensorRawBinningFactorUsed, 2> enum_values<aidl::android::hardware::camera::metadata::SensorRawBinningFactorUsed> = {
  aidl::android::hardware::camera::metadata::SensorRawBinningFactorUsed::ANDROID_SENSOR_RAW_BINNING_FACTOR_USED_TRUE,
  aidl::android::hardware::camera::metadata::SensorRawBinningFactorUsed::ANDROID_SENSOR_RAW_BINNING_FACTOR_USED_FALSE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
